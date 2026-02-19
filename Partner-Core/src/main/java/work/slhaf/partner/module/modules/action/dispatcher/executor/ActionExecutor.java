package work.slhaf.partner.module.modules.action.dispatcher.executor;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.abstracts.AbstractAgentSubModule;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.*;
import work.slhaf.partner.core.action.entity.ExecutableAction.Status;
import work.slhaf.partner.core.action.runner.RunnerClient;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.*;
import work.slhaf.partner.module.modules.action.dispatcher.scheduler.ActionScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@AgentSubModule
public class ActionExecutor extends AbstractAgentSubModule<ActionExecutorInput, Void> {

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
    @InjectModule
    private ActionScheduler actionScheduler;

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
        val actions = input.getActions();
        // 异步执行所有行动
        for (ExecutableAction executableAction : actions) {
            platformExecutor.execute(() -> {
                val source = executableAction.getSource();
                if (executableAction.getStatus() != Status.PREPARE) {
                    return;
                }
                val actionChain = executableAction.getActionChain();
                if (actionChain.isEmpty()) {
                    executableAction.setStatus(Status.FAILED);
                    executableAction.setResult("行动链为空");
                    return;
                }
                // 注册执行中行动
                val phaser = new Phaser();
                val phaserRecord = actionCapability.putPhaserRecord(phaser, executableAction);
                executableAction.setStatus(Status.EXECUTING);

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

                    val listeningRecord = executeAndListening(metaActions, phaserRecord, source);
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
                        val correctorInput = assemblyHelper.buildCorrectorInput(executableAction, source);
                        val correctorResult = actionCorrector.execute(correctorInput);
                        actionCapability.handleInterventions(correctorResult.getMetaInterventionList(), executableAction);
                    } catch (Exception ignored) {
                    }

                    // 第二次尝试进行阶段推进，本次负责补充上一次在不存在 stage时，但 corrector 执行期间发生了 actionChain 的插入事件
                    // 如果第一次已经推进完毕，本次将会跳过
                    stageCursor.requestAdvance();
                } while (stageCursor.next());

                // 结束
                actionCapability.removePhaserRecord(phaser);
                if (executableAction.getStatus() != Status.FAILED) {
                    // 如果是 ScheduledActionData, 则重置 ActionData 内容,记录执行历史与最终结果
                    if (executableAction instanceof SchedulableExecutableAction scheduledActionData) {
                        scheduledActionData.recordAndReset();
                        actionScheduler.execute(Set.of(scheduledActionData));
                    } else {
                        executableAction.setStatus(Status.SUCCESS);
                    }

                    // TODO 执行过后需要回写至任务上下文（recentCompletedTask），同时触发自对话信号进行确认并记录以及是否通知用户（触发与否需要机制进行匹配，在模块链路可增加 interaction gate 门控，判断此次对话作用于谁、由谁发出、何种性质、是否需要回应等）
                }
            });

        }
        return null;

    }

    private MetaActionsListeningRecord executeAndListening(List<MetaAction> metaActions, PhaserRecord phaserRecord, String source) {
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

                    ExecutorService executor = next.getIo() ? virtualExecutor : platformExecutor;
                    executor.execute(buildMataActionTask(next, phaserRecord, source));

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

    private Runnable buildMataActionTask(MetaAction metaAction, PhaserRecord phaserRecord, String source) {
        val phaser = phaserRecord.phaser();
        phaser.register();
        return () -> {
            val actionKey = metaAction.getKey();
            try {
                val result = metaAction.getResult();
                do {
                    val actionData = phaserRecord.executableAction();
                    val executingStage = actionData.getExecutingStage();
                    val historyActionResults = actionData.getHistory().get(executingStage);
                    val additionalContext = actionData.getAdditionalContext().get(executingStage);
                    val extractorInput = assemblyHelper.buildExtractorInput(metaAction, source, historyActionResults, additionalContext);
                    val extractorResult = paramsExtractor.execute(extractorInput);

                    if (extractorResult.isOk()) {
                        metaAction.getParams().putAll(extractorResult.getParams());
                        runnerClient.submit(metaAction);
                        val historyAction = new HistoryAction(actionKey, actionCapability.loadMetaActionInfo(actionKey).getDescription(), metaAction.getResult().getData());
                        actionData.getHistory()
                                .computeIfAbsent(executingStage, integer -> new ArrayList<>())
                                .add(historyAction);
                    } else {
                        val repairerInput = assemblyHelper.buildRepairerInput(historyActionResults, metaAction, source);
                        val repairerResult = actionRepairer.execute(repairerInput);
                        switch (repairerResult.getStatus()) {
                            // 如果本次修复被认为成功，则将补充的信息添加至 additionalContext
                            case RepairerResult.RepairerStatus.OK -> {
                                additionalContext.addAll(repairerResult.getFixedData());
                                result.setStatus(MetaAction.Result.Status.WAITING);
                            }
                            // 此处的修复失败来自系统内部的执行失败：其余方式均不可行时将回退至当前分支
                            case RepairerResult.RepairerStatus.FAILED -> {
                                result.setStatus(MetaAction.Result.Status.FAILED);
                                result.setData("行动执行失败");
                            }
                            // 此处对应已在 repairer 内发起外部请求，故在此处进行阻塞
                            case RepairerResult.RepairerStatus.ACQUIRE -> {
                                phaserRecord.interrupt();
                                result.setStatus(MetaAction.Result.Status.WAITING);
                            }
                        }
                    }
                } while (result.getStatus().equals(MetaAction.Result.Status.WAITING));
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

        private ExtractorInput buildExtractorInput(MetaAction action, String source, List<HistoryAction> historyActionResults,
                                                   List<String> additionalContext) {
            ExtractorInput input = new ExtractorInput();
            input.setEvaluatedSlices(memoryCapability.getActivatedSlices(source));
            input.setRecentMessages(cognationCapability.getChatMessages());
            input.setMetaActionInfo(actionCapability.loadMetaActionInfo(action.getKey()));
            input.setHistoryActionResults(historyActionResults);
            input.setAdditionalContext(additionalContext);
            return input;
        }

        private CorrectorInput buildCorrectorInput(ExecutableAction executableAction, String source) {
            return CorrectorInput.builder()
                    .tendency(executableAction.getTendency())
                    .source(executableAction.getSource())
                    .reason(executableAction.getReason())
                    .description(executableAction.getDescription())
                    .history(executableAction.getHistory().get(executableAction.getExecutingStage()))
                    .status(executableAction.getStatus())
                    .recentMessages(cognationCapability.getChatMessages())
                    .activatedSlices(memoryCapability.getActivatedSlices(source))
                    .build();
        }
    }

}
