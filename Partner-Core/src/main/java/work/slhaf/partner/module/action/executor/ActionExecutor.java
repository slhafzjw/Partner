package work.slhaf.partner.module.action.executor;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.*;
import work.slhaf.partner.core.action.runner.RunnerClient;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;
import work.slhaf.partner.framework.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.framework.agent.factory.context.Shutdown;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.action.executor.entity.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
public class ActionExecutor extends AbstractAgentModule.Standalone {

    private static final int MAX_EXTRACTOR_ATTEMPTS = 3;

    private final AssemblyHelper assemblyHelper = new AssemblyHelper();

    @InjectCapability
    private ActionCapability actionCapability;
    @InjectCapability
    private CognitionCapability cognitionCapability;

    @InjectModule
    private ParamsExtractor paramsExtractor;
    @InjectModule
    private ActionCorrector actionCorrector;
    @InjectModule
    private ActionCorrectionRecognizer actionCorrectionRecognizer;

    private ExecutingActionBlockManager blockManager;

    private ExecutorService virtualExecutor;
    private ExecutorService platformExecutor;
    private RunnerClient runnerClient;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Init
    public void init() {
        virtualExecutor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
        platformExecutor = actionCapability.getExecutor(ActionCore.ExecutorType.PLATFORM);
        runnerClient = actionCapability.runnerClient();
        blockManager = new ExecutingActionBlockManager(cognitionCapability.contextWorkspace());

        Set<ExecutableAction> recoveredActions = new HashSet<>();
        recoveredActions.addAll(actionCapability.listActions(Action.Status.EXECUTING, null));
        recoveredActions.addAll(actionCapability.listActions(Action.Status.INTERRUPTED, null).stream()
                .peek(executableAction -> executableAction.setStatus(Action.Status.EXECUTING))
                .collect(Collectors.toSet()));
        if (recoveredActions.isEmpty()) {
            return;
        }
        recoveredActions.forEach(this::execute);
        blockManager.emitActionRecoveredBlock(recoveredActions);
    }

    @Shutdown
    public void shutdown() {
        closed.set(true);
    }

    public void execute(Action action) {

        Future<?> future = virtualExecutor.submit(actionExecutionRouter(action));

        virtualExecutor.execute(() -> {
            try {
                future.get(action.getTimeoutMills(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                action.setStatus(Action.Status.FAILED);
                if (action instanceof ExecutableAction executableAction) {
                    ensureExecutableResult(executableAction, true, "行动执行超时");
                }
                log.warn("Action timeout, uuid: {}", action.getUuid());
            } catch (Exception ignored) {
            }
        });
    }

    private Runnable actionExecutionRouter(Action action) {
        return () -> {
            try {
                switch (action) {
                    case ExecutableAction executableAction -> handleExecutableAction(executableAction);
                    case StateAction stateAction -> handleStateAction(stateAction);
                    default -> handleUnknownAction(action);
                }
                if (action instanceof ExecutableAction executableAction) {
                    if (action.getStatus() == Action.Status.FAILED) {
                        ensureExecutableResult(executableAction, true, null);
                        emitExecutableActionFinished(executableAction);
                        return;
                    }
                    action.setStatus(Action.Status.SUCCESS);
                    ensureExecutableResult(executableAction, false, null);
                    emitExecutableActionFinished(executableAction);
                    return;
                }
                if (action.getStatus() == Action.Status.FAILED) {
                    return;
                }
                action.setStatus(Action.Status.SUCCESS);
            } catch (Exception e) {
                log.warn("Unexpected action execution failure, uuid: {}, description: {}, failure reason: {}", action.getUuid(), action.getDescription(), e.getLocalizedMessage());
                action.setStatus(Action.Status.FAILED);
                if (action instanceof ExecutableAction executableAction) {
                    ensureExecutableResult(executableAction, true, e.getLocalizedMessage());
                    emitExecutableActionFinished(executableAction);
                }
            }
        };
    }

    private void handleUnknownAction(Action action) {
        log.warn("unknown Action type: {}", action.getClass().getSimpleName());
        action.setStatus(Action.Status.FAILED);
    }

    private void emitExecutableActionFinished(ExecutableAction executableAction) {
        blockManager.emitActionFinishedBlock(executableAction);
        cognitionCapability.initiateTurn(
                "An executable action has finished. Check the latest action-finished state and inform the user of the outcome if it is relevant to the current conversation.",
                executableAction.getSource(),
                "action_planner"
        );
    }

    private void handleStateAction(StateAction stateAction) {
        if (closed.get()) {
            return;
        }
        blockManager.emitStateActionTriggeredBlock(stateAction);
        stateAction.getTrigger().onTrigger();
    }

    private void handleExecutableAction(ExecutableAction executableAction) {
        actionCapability.putAction(executableAction);

        val actionChain = executableAction.getActionChain();
        val phaser = new Phaser();
        if (!prepareExecutableAction(executableAction, actionChain)) {
            return;
        }

        blockManager.emitActionLaunchedBlock(executableAction);

        val stageCursor = initStageCursor(executableAction, actionChain);
        while (true) {
            val stageSelection = selectCurrentStage(executableAction, actionChain);
            if (stageSelection.shouldReturn()) {
                return;
            }
            if (stageSelection.shouldStop()) {
                break;
            }
            val stageExecution = runCurrentStage(executableAction, phaser, stageCursor, stageSelection.metaActions());
            if (stageExecution.closed()) {
                return;
            }
            if (!applyStageCorrectionAndAdvance(executableAction, stageCursor, stageExecution)) {
                break;
            }
        }
        finishExecutableAction(executableAction);
    }

    private boolean prepareExecutableAction(ExecutableAction executableAction, Map<Integer, List<MetaAction>> actionChain) {
        synchronized (executableAction.getExecutionLock()) {
            val status = executableAction.getStatus();
            if (status != Action.Status.PREPARE && status != Action.Status.EXECUTING) {
                return false;
            }
            if (actionChain.isEmpty()) {
                executableAction.setStatus(Action.Status.FAILED);
                executableAction.setResult("行动链为空");
                return false;
            }
            normalizeExecutingStage(executableAction, actionChain);
            executableAction.setStatus(Action.Status.EXECUTING);
            return true;
        }
    }

    private StageCursor initStageCursor(ExecutableAction executableAction, Map<Integer, List<MetaAction>> actionChain) {
        StageCursor stageCursor = new StageCursor(executableAction, actionChain);
        synchronized (executableAction.getExecutionLock()) {
            stageCursor.init();
        }
        return stageCursor;
    }

    private StageSelection selectCurrentStage(ExecutableAction executableAction, Map<Integer, List<MetaAction>> actionChain) {
        synchronized (executableAction.getExecutionLock()) {
            if (closed.get()) {
                return StageSelection.returnNow();
            }
            if (executableAction.getStatus() == Action.Status.FAILED) {
                return StageSelection.stop();
            }
            return StageSelection.continueWith(actionChain.get(executableAction.getExecutingStage()));
        }
    }

    private StageExecution runCurrentStage(
            ExecutableAction executableAction,
            Phaser phaser,
            StageCursor stageCursor,
            List<MetaAction> metaActions
    ) {
        val recognizerRecord = startRecognizerIfNeeded(executableAction, phaser);
        val listeningRecord = executeAndListening(metaActions, phaser, executableAction);
        phaser.awaitAdvance(listeningRecord.phase());
        // synchronized 同步防止 accepting 循环间、phase guard 判定后发生 stage 推进
        // 导致新行动的 phaser 投放阶段错乱无法阻塞的场景
        // 该 synchronized 将阶段推进与 accepting 监听 loop 捆绑为互斥的原子事件，避免了细粒度的 phaser 阶段竞态问题
        if (closed.get()) {
            return StageExecution.closed(recognizerRecord, metaActions);
        }
        synchronized (executableAction.getExecutionLock()) {
            synchronized (listeningRecord.accepting()) {
                listeningRecord.accepting().set(false);
                // 立即尝试推进，本次推进中，如果前方仍有未执行 stage，将执行一次阶段推进
                stageCursor.requestAdvance();
            }
        }

        blockManager.emitActionStageSettledBlock(executableAction);
        return StageExecution.completed(recognizerRecord, metaActions);
    }

    private boolean applyStageCorrectionAndAdvance(
            ExecutableAction executableAction,
            StageCursor stageCursor,
            StageExecution stageExecution
    ) {
        boolean hasFailedMetaAction = hasFailedMetaAction(stageExecution.metaActions());
        boolean shouldRunCorrector = hasFailedMetaAction;
        if (!shouldRunCorrector) {
            val recognizerResult = resolveRecognizerResult(stageExecution.recognizerRecord());
            shouldRunCorrector = recognizerResult != null && recognizerResult.isNeedCorrection();
        }
        if (shouldRunCorrector) {
            val correctorInput = assemblyHelper.buildCorrectorInput(executableAction);
            actionCorrector.execute(correctorInput)
                    .onSuccess(correctorResult -> {
                        actionCapability.handleInterventions(correctorResult.getMetaInterventionList(), executableAction);
                        blockManager.emitActionCorrectionBlock(
                                executableAction,
                                hasFailedMetaAction ? "has_failed_meta_action" : correctorResult.getCorrectionReason(),
                                correctorResult.getMetaInterventionList()
                        );
                    });
        }
        // 第二次尝试进行阶段推进，本次负责补充上一次在不存在 stage时，但 corrector 执行期间发生了 actionChain 的插入事件
        // 如果第一次已经推进完毕，本次将会跳过
        synchronized (executableAction.getExecutionLock()) {
            stageCursor.requestAdvance();
            return stageCursor.next();
        }
    }

    private void finishExecutableAction(ExecutableAction executableAction) {
        // 如果是 ScheduledActionData, 则重置 ActionData 内容,记录执行历史与最终结果
        if (executableAction instanceof SchedulableExecutableAction scheduledActionData) {
            scheduledActionData.recordAndReset();
        }
    }

    private MetaActionsListeningRecord executeAndListening(List<MetaAction> metaActions, Phaser phaser, ExecutableAction executableAction) {
        AtomicBoolean accepting = new AtomicBoolean(true);
        AtomicInteger cursor = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        val phase = phaser.register();
        platformExecutor.execute(() -> {
            boolean first = true;
            while (accepting.get()) {
                synchronized (accepting) {
                    MetaAction next = null;
                    synchronized (metaActions) {
                        if (cursor.get() < metaActions.size()) {
                            next = metaActions.get(cursor.getAndIncrement());
                        }
                    }
                    if (next == null) {
                        Thread.onSpinWait();
                        continue;
                    }
                    if (phaser.getPhase() != phase) {
                        metaActions.remove(next);
                        log.warn("行动阶段已推进，丢弃该行动: {}", next);
                        continue;
                    }
                    ExecutorService executor = next.getIo() ? virtualExecutor : platformExecutor;
                    executor.execute(buildMataActionTask(next, phaser, executableAction));
                    if (first) {
                        phaser.arriveAndDeregister();
                        latch.countDown();
                        first = false;
                    }
                }
            }
        });
        try {
            // 确保执行一次，防止没来得及注册任务就已经结束
            latch.await();
        } catch (InterruptedException ignored) {
        }
        return new MetaActionsListeningRecord(accepting, phase);
    }

    private Runnable buildMataActionTask(MetaAction metaAction, Phaser phaser, ExecutableAction executableAction) {
        phaser.register();
        return () -> {
            val actionKey = metaAction.getKey();
            try {
                executeMetaActionWithRetry(metaAction, executableAction);
            } catch (Exception e) {
                log.error("Action executing failed: {}", actionKey, e);
            } finally {
                phaser.arriveAndDeregister();
            }
        };
    }

    private void executeMetaActionWithRetry(MetaAction metaAction, ExecutableAction actionData) {
        AtomicReference<String> failureReason = new AtomicReference<>("参数提取失败");
        int executingStage = actionData.getExecutingStage();
        boolean succeeded = false;
        for (int attempt = 1; attempt <= MAX_EXTRACTOR_ATTEMPTS; attempt++) {
            val result = metaAction.getResult();
            result.reset();
            metaAction.getParams().clear();

            Result<ExtractorInput> extractorInputResult = assemblyHelper.buildExtractorInput(metaAction.getKey(), actionData.getUuid(), actionData.getDescription());
            AgentRuntimeException exception = extractorInputResult.exceptionOrNull();
            if (exception != null) {
                failureReason.set(exception.getMessage());
                break;
            }

            ExtractorInput extractorInput = extractorInputResult.getOrThrow();
            Result<ExtractorResult> extractorResultWrapped = paramsExtractor.execute(extractorInput).onFailure(exp -> failureReason.set(exp.getLocalizedMessage()));
            if (extractorResultWrapped.exceptionOrNull() != null) {
                continue;
            }

            ExtractorResult extractorResult = extractorResultWrapped.getOrThrow();
            if (!extractorResult.isOk()) {
                failureReason.set(buildAttemptFailureReason("参数提取失败", null));
                continue;
            }
            metaAction.getParams().putAll(toMetaActionParams(extractorResult.getParams()));

            try {
                runnerClient.submit(metaAction);
            } catch (Exception e) {
                failureReason.set(buildAttemptFailureReason("行动执行异常", e.getLocalizedMessage()));
                continue;
            }

            if (result.getStatus() == MetaAction.Result.Status.SUCCESS) {
                succeeded = true;
                break;
            }

            failureReason.set(buildAttemptFailureReason("行动执行失败", result.getData()));
        }
        if (!succeeded) {
            metaAction.getResult().setStatus(MetaAction.Result.Status.FAILED);
            metaAction.getResult().setData(failureReason.get());
        }
        appendHistoryAction(actionData, executingStage, metaAction);
    }

    private Map<String, Object> toMetaActionParams(List<ExtractorResult.ParamEntry> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        for (ExtractorResult.ParamEntry entry : params) {
            if (entry == null) {
                continue;
            }
            String name = entry.getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            converted.put(name, entry.getValue());
        }
        return converted;
    }

    private void appendHistoryAction(ExecutableAction actionData, int executingStage, MetaAction metaAction) {
        HistoryAction historyAction = new HistoryAction(
                metaAction.getKey(),
                resolveHistoryDescription(metaAction.getKey()),
                metaAction.getResult().getData()
        );
        actionData.getHistory()
                .computeIfAbsent(executingStage, integer -> new ArrayList<>())
                .add(historyAction);
    }

    private RecognizerTaskRecord startRecognizerIfNeeded(ExecutableAction executableAction, Phaser phaser) {
        if (!shouldRunCorrectionRecognizer(executableAction)) {
            return RecognizerTaskRecord.disabled();
        }
        val recognizerInput = assemblyHelper.buildCorrectorInput(executableAction);
        val task = buildRecognizerTask(recognizerInput, phaser);
        Future<RecognizerResult> future = virtualExecutor.submit(task);
        return new RecognizerTaskRecord(true, future);
    }

    private Callable<RecognizerResult> buildRecognizerTask(CorrectorInput input, Phaser phaser) {
        phaser.register();
        return () -> {
            try {
                return actionCorrectionRecognizer.execute(input)
                        .getOrDefault(new RecognizerResult());
            } finally {
                phaser.arriveAndDeregister();
            }
        };
    }

    private RecognizerResult resolveRecognizerResult(RecognizerTaskRecord record) {
        if (record == null || !record.enabled() || record.future() == null) {
            return null;
        }
        try {
            if (!record.future().isDone()) {
                return null;
            }
            return record.future().get();
        } catch (Exception e) {
            return null;
        }
    }

    private String buildAttemptFailureReason(String prefix, String detail) {
        if (detail == null || detail.isBlank()) {
            return prefix;
        }
        return prefix + ": " + detail;
    }

    private boolean hasFailedMetaAction(List<MetaAction> metaActions) {
        return metaActions.stream().anyMatch(metaAction -> metaAction.getResult().getStatus() == MetaAction.Result.Status.FAILED);
    }

    private boolean shouldRunCorrectionRecognizer(ExecutableAction executableAction) {
        val orderedStages = new ArrayList<>(executableAction.getActionChain().keySet());
        orderedStages.sort(Integer::compareTo);
        int totalStages = orderedStages.size();
        if (totalStages < 3) {
            return false;
        }
        int stageIndex = orderedStages.indexOf(executableAction.getExecutingStage());
        if (stageIndex < 0) {
            return false;
        }
        if (stageIndex == totalStages - 1) {
            return true;
        }
        return stageIndex >= 2 && (stageIndex - 2) % 2 == 0;
    }

    private void normalizeExecutingStage(ExecutableAction executableAction, Map<Integer, List<MetaAction>> actionChain) {
        Integer firstStage = actionChain.keySet().stream()
                .min(Integer::compareTo)
                .orElse(null);
        if (firstStage == null) {
            return;
        }
        if (actionChain.containsKey(executableAction.getExecutingStage())) {
            return;
        }
        if (executableAction.getStatus() == Action.Status.EXECUTING) {
            resetExecutableActionForReplay(executableAction);
        }
        executableAction.setExecutingStage(firstStage);
    }

    private void resetExecutableActionForReplay(ExecutableAction executableAction) {
        executableAction.getHistory().clear();
        executableAction.getActionChain().values().forEach(metaActions -> metaActions.forEach(metaAction -> {
            metaAction.getParams().clear();
            metaAction.getResult().reset();
        }));
        if (hasExecutableResult(executableAction)) {
            executableAction.setResult("");
        }
    }

    private void ensureExecutableResult(ExecutableAction executableAction, boolean failed, String failureReason) {
        if (hasExecutableResult(executableAction)) {
            return;
        }
        executableAction.setResult(resolveExecutableResult(executableAction, failed, failureReason));
    }

    private String resolveExecutableResult(ExecutableAction executableAction, boolean failed, String failureReason) {
        String extracted = extractLastMetaActionResult(executableAction);
        if (extracted != null && !extracted.isBlank()) {
            return extracted;
        }
        if (!failed) {
            return "行动执行成功";
        }
        if (failureReason != null && !failureReason.isBlank()) {
            return "行动执行失败: " + failureReason;
        }
        return "行动执行失败";
    }

    private String extractLastMetaActionResult(ExecutableAction executableAction) {
        if (!executableAction.getHistory().isEmpty()) {
            Integer lastStage = executableAction.getHistory().keySet().stream()
                    .max(Integer::compareTo)
                    .orElse(null);
            if (lastStage != null) {
                List<HistoryAction> historyActions = executableAction.getHistory().get(lastStage);
                if (historyActions != null && !historyActions.isEmpty()) {
                    String result = historyActions.getLast().result();
                    if (result != null && !result.isBlank()) {
                        return result;
                    }
                }
            }
        }

        if (!executableAction.getActionChain().isEmpty()) {
            Integer lastStage = executableAction.getActionChain().keySet().stream()
                    .max(Integer::compareTo)
                    .orElse(null);
            if (lastStage != null) {
                List<MetaAction> metaActions = executableAction.getActionChain().get(lastStage);
                if (metaActions != null && !metaActions.isEmpty()) {
                    String result = metaActions.getLast().getResult().getData();
                    if (result != null && !result.isBlank()) {
                        return result;
                    }
                }
            }
        }
        return null;
    }

    private boolean hasExecutableResult(ExecutableAction executableAction) {
        try {
            String result = executableAction.getResult();
            return result != null && !result.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveHistoryDescription(String actionKey) {
        return actionCapability.loadMetaActionInfo(actionKey)
                .fold(
                        metaActionInfo -> metaActionInfo.getDescription().isBlank() ? actionKey : metaActionInfo.getDescription(),
                        exception -> actionKey
                );
    }

    private enum StageSelectionType {
        CONTINUE,
        STOP,
        RETURN
    }

    private record StageSelection(StageSelectionType type, List<MetaAction> metaActions) {
        private static StageSelection continueWith(List<MetaAction> metaActions) {
            return new StageSelection(StageSelectionType.CONTINUE, metaActions);
        }

        private static StageSelection stop() {
            return new StageSelection(StageSelectionType.STOP, null);
        }

        private static StageSelection returnNow() {
            return new StageSelection(StageSelectionType.RETURN, null);
        }

        private boolean shouldStop() {
            return type == StageSelectionType.STOP;
        }

        private boolean shouldReturn() {
            return type == StageSelectionType.RETURN;
        }
    }

    private record StageExecution(RecognizerTaskRecord recognizerRecord, List<MetaAction> metaActions, boolean closed) {
        private static StageExecution completed(RecognizerTaskRecord recognizerRecord, List<MetaAction> metaActions) {
            return new StageExecution(recognizerRecord, metaActions, false);
        }

        private static StageExecution closed(RecognizerTaskRecord recognizerRecord, List<MetaAction> metaActions) {
            return new StageExecution(recognizerRecord, metaActions, true);
        }
    }

    private record MetaActionsListeningRecord(AtomicBoolean accepting, int phase) {
    }

    private record RecognizerTaskRecord(boolean enabled, Future<RecognizerResult> future) {
        private static RecognizerTaskRecord disabled() {
            return new RecognizerTaskRecord(false, null);
        }
    }

    private static final class StageCursor {
        private final ExecutableAction executableAction;
        private final Map<Integer, List<MetaAction>> actionChain;

        private int stageCount;
        private boolean executingStageUpdated;
        private boolean stageCountUpdated;

        private StageCursor(ExecutableAction executableAction, Map<Integer, List<MetaAction>> actionChain) {
            this.executableAction = executableAction;
            this.actionChain = actionChain;
        }

        private void init() {
            val orderList = new ArrayList<>(actionChain.keySet());
            orderList.sort(Integer::compareTo);
            stageCount = orderList.indexOf(executableAction.getExecutingStage());
            update();
        }

        private void requestAdvance() {
            if (!stageCountUpdated) {
                stageCount++;
                stageCountUpdated = true;
            }
            if (stageCount < actionChain.size() && !executingStageUpdated) {
                update();
                executingStageUpdated = true;
            }
        }

        private boolean next() {
            executingStageUpdated = false;
            stageCountUpdated = false;
            return stageCount < actionChain.size();
        }

        private void update() {
            val orderList = new ArrayList<>(actionChain.keySet());
            orderList.sort(Integer::compareTo);
            executableAction.setExecutingStage(orderList.get(stageCount));
        }
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    private class AssemblyHelper {
        private AssemblyHelper() {
        }

        private Result<ExtractorInput> buildExtractorInput(String actionKey, @NotNull String uuid, @NotNull String description) {
            return actionCapability.loadMetaActionInfo(actionKey).fold(
                    metaActionInfo -> {
                        ExtractorInput input = new ExtractorInput();
                        input.setMetaActionInfo(metaActionInfo);
                        input.setTargetActionId(uuid);
                        input.setTargetActionDesc(description);
                        return Result.success(input);
                    },
                    Result::failure
            );
        }

        private CorrectorInput buildCorrectorInput(ExecutableAction executableAction) {
            Map<Integer, List<CorrectorInput.ActionChainItem>> overview = new LinkedHashMap<>();
            executableAction.getActionChain().forEach((stage, list) -> {
                List<CorrectorInput.ActionChainItem> overviewItems = list.stream()
                        .map(metaAction -> new CorrectorInput.ActionChainItem(
                                metaAction.getKey(),
                                resolveHistoryDescription(metaAction.getKey()),
                                metaAction.getResult().getStatus().name().toLowerCase(Locale.ROOT)
                        ))
                        .toList();
                overview.put(stage, overviewItems);
            });
            return CorrectorInput.builder()
                    .tendency(executableAction.getTendency())
                    .source(executableAction.getSource())
                    .reason(executableAction.getReason())
                    .description(executableAction.getDescription())
                    .actionId(executableAction.getUuid())
                    .actionChainOverview(overview)
                    .build();
        }

    }
}
