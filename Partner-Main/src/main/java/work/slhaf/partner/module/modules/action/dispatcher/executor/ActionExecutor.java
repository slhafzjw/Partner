package work.slhaf.partner.module.modules.action.dispatcher.executor;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.*;
import work.slhaf.partner.core.action.entity.ActionData.ActionStatus;
import work.slhaf.partner.core.action.runner.RunnerClient;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@AgentSubModule
public class ActionExecutor extends AgentRunningSubModule<ActionExecutorInput, Void> {

    @InjectCapability
    private ActionCapability actionCapability;
    @InjectCapability
    private MemoryCapability memoryCapability;
    @InjectCapability
    private CognationCapability cognationCapability;

    @InjectModule
    private ParamsExtractor paramsExtractor;
    @InjectModule
    private ActionRepairer actionRepairer;
    @InjectModule
    private ActionCorrector actionCorrector;

    private ExecutorService virtualExecutor;
    private ExecutorService platformExecutor;
    private RunnerClient runnerClient;

    private final AssemblyHelper assemblyHelper = new AssemblyHelper();

    @Init
    public void init() {
        virtualExecutor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
        platformExecutor = actionCapability.getExecutor(ActionCore.ExecutorType.PLATFORM);
        runnerClient = actionCapability.runnerClient();
    }

    /**
     * 执行行动
     *
     * @param input ActionExecutor 输入内容
     * @return 无返回，执行结果回写至 input 内部携带的 actionData 中
     */
    @Override
    public Void execute(ActionExecutorInput input) {
        val immediateActions = input.getImmediateActions();
        val userId = input.getUserId();
        // 异步执行所有行动
        for (ImmediateActionData actionData : immediateActions) {
            platformExecutor.execute(() -> {
                if (actionData.getStatus() != ActionData.ActionStatus.PREPARE) {
                    return;
                }
                val actionChain = actionData.getActionChain();
                if (actionChain.isEmpty()) {
                    actionData.setStatus(ActionStatus.FAILED);
                    actionData.setResult("行动链为空");
                    return;
                }
                // 注册执行中行动
                val phaser = new Phaser();
                val phaserRecord = actionCapability.putPhaserRecord(phaser, actionData);
                actionData.setStatus(ActionData.ActionStatus.EXECUTING);

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
                        actionData.setExecutingStage(orderList.get(stageCount));
                    }
                };

                stageCursor.init();
                do {
                    val metaActions = actionChain.get(actionData.getExecutingStage());

                    val listeningRecord = executeAndListening(metaActions, phaserRecord, userId);
                    phaser.awaitAdvance(listeningRecord.phase());

                    // synchronized 同步防止 accepting 循环间、phase guard 判定后发生 stage 推进
                    // 导致新行动的 phaser 投放阶段错乱无法阻塞的场景
                    // 该 synchronized 将阶段推进与 accepting 监听 loop 捆绑为互斥的原子事件，避免了细粒度的 phaser 阶段竞态问题
                    synchronized (listeningRecord.accepting()) {
                        listeningRecord.accepting().set(false);

                        // 立即尝试推进，本次推进中，如果前方仍有未执行 stage，将执行一次阶段推进
                        stageCursor.requestAdvance();
                    }

                    try {
                        // 针对行动链进行修正，修正需要传入执行历史、行动目标等内容
                        // 如果后续运行 corrector 触发频率较高，可考虑增加重试机制
                        val correctorInput = assemblyHelper.buildCorrectorInput(actionData, userId);
                        val correctorResult = actionCorrector.execute(correctorInput);
                        actionCapability.handleInterventions(correctorResult.getMetaInterventionList(), actionData);
                    } catch (Exception ignored) {
                    }

                    // 第二次尝试进行阶段推进，本次负责补充上一次在不存在 stage时，但 corrector 执行期间发生了 actionChain 的插入事件
                    // 如果第一次已经推进完毕，本次将会跳过
                    stageCursor.requestAdvance();
                } while (stageCursor.next());

                // 结束
                actionCapability.removePhaserRecord(phaser);
                if (actionData.getStatus() != ActionData.ActionStatus.FAILED) {
                    actionData.setStatus(ActionStatus.SUCCESS);
                }
            });

        }
        return null;

    }

    private MetaActionsListeningRecord executeAndListening(List<MetaAction> metaActions, PhaserRecord phaserRecord, String userId) {
        AtomicBoolean accepting = new AtomicBoolean(true);
        AtomicInteger cursor = new AtomicInteger();

        CountDownLatch latch = new CountDownLatch(1);
        val phaser = phaserRecord.phaser();
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

                    ExecutorService executor = next.isIo() ? virtualExecutor : platformExecutor;
                    executor.execute(buildMataActionTask(next, phaserRecord, userId));

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

    private Runnable buildMataActionTask(MetaAction metaAction, PhaserRecord phaserRecord, String userId) {
        val phaser = phaserRecord.phaser();
        phaser.register();
        return () -> {
            val actionKey = metaAction.getKey();
            try {
                val result = metaAction.getResult();
                do {
                    val actionData = phaserRecord.actionData();
                    val executingStage = actionData.getExecutingStage();
                    val historyActionResults = actionData.getHistory().get(executingStage);
                    val additionalContext = actionData.getAdditionalContext().get(executingStage);
                    val extractorInput = assemblyHelper.buildExtractorInput(metaAction, userId, historyActionResults, additionalContext);
                    val extractorResult = paramsExtractor.execute(extractorInput);

                    if (extractorResult.isOk()) {
                        metaAction.setParams(extractorResult.getParams());
                        runnerClient.submit(metaAction);
                        val historyAction = new HistoryAction();
                        historyAction.setActionKey(actionKey);
                        historyAction.setDescription(actionCapability.loadMetaActionInfo(actionKey).getDescription());
                        historyAction.setResult(metaAction.getResult().getData());
                        actionData.getHistory()
                                .computeIfAbsent(executingStage, integer -> new ArrayList<>())
                                .add(historyAction);
                    } else {
                        val repairerInput = assemblyHelper.buildRepairerInput(historyActionResults, metaAction, userId);
                        val repairerResult = actionRepairer.execute(repairerInput);
                        switch (repairerResult.getStatus()) {
                            // 如果本次修复被认为成功，则将补充的信息添加至 additionalContext
                            case RepairerResult.RepairerStatus.OK -> {
                                additionalContext.addAll(repairerResult.getFixedData());
                                result.setStatus(MetaAction.ResultStatus.WAITING);
                            }
                            // 此处的修复失败来自系统内部的执行失败：其余方式均不可行时将回退至当前分支
                            case RepairerResult.RepairerStatus.FAILED -> {
                                result.setStatus(MetaAction.ResultStatus.FAILED);
                                result.setData("行动执行失败");
                            }
                            // 此处对应已在 repairer 内发起外部请求，故在此处进行阻塞
                            case RepairerResult.RepairerStatus.ACQUIRE -> {
                                phaserRecord.interrupt();
                                result.setStatus(MetaAction.ResultStatus.WAITING);
                            }
                        }
                    }
                } while (result.getStatus().equals(MetaAction.ResultStatus.WAITING));
            } catch (Exception e) {
                log.error("Action executing failed: {}", actionKey, e);
            } finally {
                phaser.arriveAndDeregister();
            }
        };
    }

    private record MetaActionsListeningRecord(AtomicBoolean accepting, int phase) {
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    private class AssemblyHelper {

        private AssemblyHelper() {
        }

        private RepairerInput buildRepairerInput(List<HistoryAction> historyActionsResults, MetaAction action, String userId) {
            RepairerInput input = new RepairerInput();
            MetaActionInfo metaActionInfo = actionCapability.loadMetaActionInfo(action.getKey());
            input.setHistoryActionResults(historyActionsResults);
            input.setParams(metaActionInfo.getParams());
            input.setRecentMessages(cognationCapability.getChatMessages());
            input.setActionDescription(metaActionInfo.getDescription());
            input.setUserId(userId);
            return input;
        }

        private ExtractorInput buildExtractorInput(MetaAction action, String userId, List<HistoryAction> historyActionResults,
                                                   List<String> additionalContext) {
            ExtractorInput input = new ExtractorInput();
            input.setEvaluatedSlices(memoryCapability.getActivatedSlices(userId));
            input.setRecentMessages(cognationCapability.getChatMessages());
            input.setMetaActionInfo(actionCapability.loadMetaActionInfo(action.getKey()));
            input.setHistoryActionResults(historyActionResults);
            input.setAdditionalContext(additionalContext);
            return input;
        }

        private CorrectorInput buildCorrectorInput(ImmediateActionData actionData, String userId) {
            return CorrectorInput.builder()
                    .tendency(actionData.getTendency())
                    .source(actionData.getSource())
                    .reason(actionData.getReason())
                    .description(actionData.getDescription())
                    .history(actionData.getHistory().get(actionData.getExecutingStage()))
                    .status(actionData.getStatus())
                    .recentMessages(cognationCapability.getChatMessages())
                    .activatedSlices(memoryCapability.getActivatedSlices(userId))
                    .build();
        }
    }

}
