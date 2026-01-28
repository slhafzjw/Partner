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
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;

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
                // 注册执行中行动
                val phaser = new Phaser();
                phaser.register();
                val phaserRecord = actionCapability.putPhaserRecord(phaser, actionData);
                actionData.setStatus(ActionData.ActionStatus.EXECUTING);

                // 开始执行
                val actionChain = actionData.getActionChain();
                int stageCount = 0;
                do {
                    val orderList = new ArrayList<>(actionChain.keySet());
                    orderList.sort(Integer::compareTo);
                    val executingStage = orderList.get(stageCount);
                    actionData.setExecutingStage(executingStage);

                    val metaActions = actionChain.get(executingStage);
                    val phase = phaser.bulkRegister(metaActions.size());
                    for (MetaAction metaAction : metaActions) {
                        val executor = metaAction.isIo() ? virtualExecutor : platformExecutor;
                        executor.execute(buildMataActionTask(metaAction, phaserRecord, userId));
                    }
                    phaser.awaitAdvance(phase);

                    // TODO 进行行动链修正
                    val correctorInput = assemblyHelper.buildCorrectorInput();
                    actionCorrector.execute(correctorInput);
                } while (actionChain.size() > ++stageCount);

                // 结束
                phaser.arriveAndDeregister();
                actionCapability.removePhaserRecord(phaser);
                if (actionData.getStatus() != ActionData.ActionStatus.FAILED) {
                    actionData.setStatus(ActionStatus.SUCCESS);
                }
            });

        }
        return null;

    }

    private Runnable buildMataActionTask(MetaAction metaAction, PhaserRecord phaserRecord, String userId) {
        return () -> {
            try {
                val result = metaAction.getResult();
                do {
                    val actionData = phaserRecord.actionData();
                    val historyActionResults = assemblyHelper.getHistoryActionResults(actionData);
                    val additionalContext = actionData.getAdditionalContext().get(actionData.getExecutingStage());
                    val extractorInput = assemblyHelper.buildExtractorInput(metaAction, userId, historyActionResults, additionalContext);
                    val extractorResult = paramsExtractor.execute(extractorInput);

                    if (extractorResult.isOk()) {
                        metaAction.setParams(extractorResult.getParams());
                        runnerClient.submit(metaAction);
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
                // TODO 执行结果不再需要写入特定位置，当前的 ActionCapability
                // 内部的行动池已经足以承担这个功能，但这也就意味着行动池或许需要考虑特殊的序列化形式避免内存占用过高,
                // 同时也需要在某些模块执行时加上行动结果的挑取作为输入内容
            } catch (Exception e) {
                log.error("Action executing failed: {}", metaAction.getKey(), e);
            } finally {
                phaserRecord.phaser().arriveAndDeregister();
            }
        };
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

        private List<HistoryAction> getHistoryActionResults(ActionData actionData) {
            int executingStage = actionData.getExecutingStage();
            if (executingStage <= 0) {
                return new ArrayList<>();
            }
            Map<Integer, List<MetaAction>> actionChain = actionData.getActionChain();
            // executingStage 是当前正在执行的阶段，所以只需要获取到前一阶段的结果
            return actionChain.get(executingStage - 1).stream()
                    .map(metaAction -> {
                        HistoryAction historyAction = new HistoryAction();
                        historyAction.setActionKey(metaAction.getKey());
                        historyAction
                                .setDescription(
                                        actionCapability.loadMetaActionInfo(metaAction.getKey()).getDescription());
                        historyAction.setResult(metaAction.getResult().getData());
                        return historyAction;
                    }).toList();
        }

        private CorrectorInput buildCorrectorInput() {
            return null;
        }
    }

}
