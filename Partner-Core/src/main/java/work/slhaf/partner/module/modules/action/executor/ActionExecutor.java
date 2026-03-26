package work.slhaf.partner.module.modules.action.executor;

import lombok.val;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.annotation.Init;
import work.slhaf.partner.api.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.*;
import work.slhaf.partner.core.action.runner.RunnerClient;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.module.modules.action.executor.entity.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ActionExecutor extends AbstractAgentModule.Standalone {

    private static final int MAX_EXTRACTOR_ATTEMPTS = 3;

    private final AssemblyHelper assemblyHelper = new AssemblyHelper();

    @InjectCapability
    private ActionCapability actionCapability;
    @InjectCapability
    private MemoryCapability memoryCapability;
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

    @Init
    public void init() {
        virtualExecutor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
        platformExecutor = actionCapability.getExecutor(ActionCore.ExecutorType.PLATFORM);
        runnerClient = actionCapability.runnerClient();
        blockManager = new ExecutingActionBlockManager(cognitionCapability.contextWorkspace());
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
                        blockManager.emitActionFinishedBlock(executableAction);
                        return;
                    }
                    action.setStatus(Action.Status.SUCCESS);
                    ensureExecutableResult(executableAction, false, null);
                    blockManager.emitActionFinishedBlock(executableAction);
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
                    blockManager.emitActionFinishedBlock(executableAction);
                }
            }
        };
    }

    private void handleUnknownAction(Action action) {
        log.warn("unknown Action type: {}", action.getClass().getSimpleName());
        action.setStatus(Action.Status.FAILED);
    }

    private void handleStateAction(StateAction stateAction) {
        blockManager.emitStateActionTriggeredBlock(stateAction);
        stateAction.getTrigger().onTrigger();
    }

    private void handleExecutableAction(ExecutableAction executableAction) {
        val source = executableAction.getSource();
        if (executableAction.getStatus() != Action.Status.PREPARE) {
            return;
        }
        val actionChain = executableAction.getActionChain();
        if (actionChain.isEmpty()) {
            executableAction.setStatus(Action.Status.FAILED);
            executableAction.setResult("行动链为空");
            return;
        }
        // 注册执行中行动
        val phaser = new Phaser();
        executableAction.setStatus(Action.Status.EXECUTING);

        blockManager.emitActionLaunchedBlock(executableAction);

        // 开始执行
        val stageCursor = new Object() {
            int stageCount;
            boolean executingStageUpdated;
            boolean stageCountUpdated;

            void init() {
                stageCount = 0;
                executingStageUpdated = false;
                stageCountUpdated = false;
                update();
            }

            void requestAdvance() {
                if (!stageCountUpdated) {
                    stageCount++;
                    stageCountUpdated = true;
                }
                if (stageCount < actionChain.size() && !executingStageUpdated) {
                    update();
                    executingStageUpdated = true;
                }
            }

            boolean next() {
                executingStageUpdated = false;
                stageCountUpdated = false;
                return stageCount < actionChain.size();
            }

            void update() {
                val orderList = new ArrayList<>(actionChain.keySet());
                orderList.sort(Integer::compareTo);
                executableAction.setExecutingStage(orderList.get(stageCount));
            }
        };
        stageCursor.init();
        do {
            val metaActions = actionChain.get(executableAction.getExecutingStage());
            val recognizerRecord = startRecognizerIfNeeded(executableAction, phaser);
            val listeningRecord = executeAndListening(metaActions, phaser, executableAction, source);
            phaser.awaitAdvance(listeningRecord.phase());
            // synchronized 同步防止 accepting 循环间、phase guard 判定后发生 stage 推进
            // 导致新行动的 phaser 投放阶段错乱无法阻塞的场景
            // 该 synchronized 将阶段推进与 accepting 监听 loop 捆绑为互斥的原子事件，避免了细粒度的 phaser 阶段竞态问题
            synchronized (listeningRecord.accepting()) {
                listeningRecord.accepting().set(false);
                // 立即尝试推进，本次推进中，如果前方仍有未执行 stage，将执行一次阶段推进
                stageCursor.requestAdvance();
            }

            blockManager.emitActionStageSettledBlock(executableAction);

            boolean hasFailedMetaAction = hasFailedMetaAction(metaActions);
            boolean shouldRunCorrector = hasFailedMetaAction;
            try {
                if (!shouldRunCorrector) {
                    val recognizerResult = resolveRecognizerResult(recognizerRecord);
                    shouldRunCorrector = recognizerResult != null && recognizerResult.isNeedCorrection();
                }
                if (shouldRunCorrector) {
                    val correctorInput = assemblyHelper.buildCorrectorInput(executableAction);
                    val correctorResult = actionCorrector.execute(correctorInput);
                    actionCapability.handleInterventions(correctorResult.getMetaInterventionList(), executableAction);

                    blockManager.emitActionCorrectionBlock(
                            executableAction,
                            hasFailedMetaAction ? "has_failed_meta_action" : correctorResult.getCorrectionReason(),
                            correctorResult.getMetaInterventionList()
                    );

                }
            } catch (Exception ignored) {
            }
            // 第二次尝试进行阶段推进，本次负责补充上一次在不存在 stage时，但 corrector 执行期间发生了 actionChain 的插入事件
            // 如果第一次已经推进完毕，本次将会跳过
            stageCursor.requestAdvance();
        } while (stageCursor.next());
        // 如果是 ScheduledActionData, 则重置 ActionData 内容,记录执行历史与最终结果
        if (executableAction instanceof SchedulableExecutableAction scheduledActionData) {
            scheduledActionData.recordAndReset();
        }
    }

    private MetaActionsListeningRecord executeAndListening(List<MetaAction> metaActions, Phaser phaser, ExecutableAction executableAction, String source) {
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
                    executor.execute(buildMataActionTask(next, phaser, executableAction, source));
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

    private Runnable buildMataActionTask(MetaAction metaAction, Phaser phaser, ExecutableAction executableAction, String source) {
        phaser.register();
        return () -> {
            val actionKey = metaAction.getKey();
            try {
                executeMetaActionWithRetry(metaAction, executableAction, source);
            } catch (Exception e) {
                log.error("Action executing failed: {}", actionKey, e);
            } finally {
                phaser.arriveAndDeregister();
            }
        };
    }

    private void executeMetaActionWithRetry(MetaAction metaAction, ExecutableAction actionData, String source) {
        String failureReason = "参数提取失败";
        val actionKey = metaAction.getKey();
        for (int attempt = 1; attempt <= MAX_EXTRACTOR_ATTEMPTS; attempt++) {
            val result = metaAction.getResult();
            result.reset();
            metaAction.getParams().clear();

            val executingStage = actionData.getExecutingStage();
            val historyActionResults = actionData.getHistory().get(executingStage);
            val additionalContext = actionData.getAdditionalContext().get(executingStage);
            val extractorInput = assemblyHelper.buildExtractorInput(metaAction, source, historyActionResults, additionalContext);
            ExtractorResult extractorResult;
            try {
                extractorResult = paramsExtractor.execute(extractorInput);
            } catch (Exception e) {
                failureReason = buildAttemptFailureReason("参数提取异常", e.getLocalizedMessage());
                continue;
            }

            if (extractorResult == null || !extractorResult.isOk()) {
                failureReason = buildAttemptFailureReason("参数提取失败", null);
                continue;
            }

            if (extractorResult.getParams() != null) {
                metaAction.getParams().putAll(extractorResult.getParams());
            }

            try {
                runnerClient.submit(metaAction);
            } catch (Exception e) {
                failureReason = buildAttemptFailureReason("行动执行异常", e.getLocalizedMessage());
                continue;
            }

            if (result.getStatus() == MetaAction.Result.Status.SUCCESS) {
                val historyAction = new HistoryAction(actionKey, actionCapability.loadMetaActionInfo(actionKey).getDescription(), result.getData());
                actionData.getHistory()
                        .computeIfAbsent(executingStage, integer -> new ArrayList<>())
                        .add(historyAction);
                return;
            }

            failureReason = buildAttemptFailureReason("行动执行失败", result.getData());
        }
        metaAction.getResult().setStatus(MetaAction.Result.Status.FAILED);
        metaAction.getResult().setData(failureReason);
    }

    private RecognizerTaskRecord startRecognizerIfNeeded(ExecutableAction executableAction, Phaser phaser) {
        if (!shouldRunCorrectionRecognizer(executableAction)) {
            return RecognizerTaskRecord.disabled();
        }
        val recognizerInput = assemblyHelper.buildRecognizerInput(executableAction);
        val task = buildRecognizerTask(recognizerInput, phaser);
        Future<CorrectionRecognizerResult> future = virtualExecutor.submit(task);
        return new RecognizerTaskRecord(true, future);
    }

    private Callable<CorrectionRecognizerResult> buildRecognizerTask(CorrectionRecognizerInput input, Phaser phaser) {
        phaser.register();
        return () -> {
            try {
                return actionCorrectionRecognizer.execute(input);
            } finally {
                phaser.arriveAndDeregister();
            }
        };
    }

    private CorrectionRecognizerResult resolveRecognizerResult(RecognizerTaskRecord record) {
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

    private record MetaActionsListeningRecord(AtomicBoolean accepting, int phase) {
    }

    private record RecognizerTaskRecord(boolean enabled, Future<CorrectionRecognizerResult> future) {
        private static RecognizerTaskRecord disabled() {
            return new RecognizerTaskRecord(false, null);
        }
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    private class AssemblyHelper {
        private AssemblyHelper() {
        }

        private ExtractorInput buildExtractorInput(MetaAction action, String source, List<HistoryAction> historyActionResults,
                                                   List<String> additionalContext) {
            ExtractorInput input = new ExtractorInput();
            input.setActivatedMemorySlices(memoryCapability.getActivatedSlices());
            input.setRecentMessages(cognitionCapability.getChatMessages());
            input.setMetaActionInfo(actionCapability.loadMetaActionInfo(action.getKey()));
            input.setHistoryActionResults(historyActionResults);
            input.setAdditionalContext(additionalContext);
            return input;
        }

        private CorrectorInput buildCorrectorInput(ExecutableAction executableAction) {
            return CorrectorInput.builder()
                    .tendency(executableAction.getTendency())
                    .source(executableAction.getSource())
                    .reason(executableAction.getReason())
                    .description(executableAction.getDescription())
                    .history(executableAction.getHistory().get(executableAction.getExecutingStage()))
                    .status(executableAction.getStatus())
                    .recentMessages(cognitionCapability.getChatMessages())
                    .activatedSlices(memoryCapability.getActivatedSlices())
                    .build();
        }

        private CorrectionRecognizerInput buildRecognizerInput(ExecutableAction executableAction) {
            val orderedStages = new ArrayList<>(executableAction.getActionChain().keySet());
            orderedStages.sort(Integer::compareTo);
            int currentStageIndex = orderedStages.indexOf(executableAction.getExecutingStage());
            List<CorrectionRecognizerMetaActionSnapshot> currentStageMetaActions = executableAction.getActionChain()
                    .getOrDefault(executableAction.getExecutingStage(), List.of())
                    .stream()
                    .map(metaAction -> CorrectionRecognizerMetaActionSnapshot.builder()
                            .key(metaAction.getKey())
                            .name(metaAction.getName())
                            .io(metaAction.getIo())
                            .resultStatus(metaAction.getResult().getStatus().name())
                            .resultData(metaAction.getResult().getData())
                            .build())
                    .toList();
            return CorrectionRecognizerInput.builder()
                    .tendency(executableAction.getTendency())
                    .source(executableAction.getSource())
                    .reason(executableAction.getReason())
                    .description(executableAction.getDescription())
                    .history(executableAction.getHistory().get(executableAction.getExecutingStage()))
                    .currentStageMetaActions(currentStageMetaActions)
                    .orderedStages(orderedStages)
                    .currentStage(executableAction.getExecutingStage())
                    .currentStageIndex(currentStageIndex)
                    .lastStage(currentStageIndex == orderedStages.size() - 1)
                    .recentMessages(cognitionCapability.getChatMessages())
                    .activatedSlices(memoryCapability.getActivatedSlices())
                    .build();
        }
    }
}
