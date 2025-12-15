package work.slhaf.partner.module.modules.action.dispatcher.executor;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.*;
import work.slhaf.partner.core.action.entity.ActionData.ActionStatus;
import work.slhaf.partner.core.action.entity.MetaAction.ResultStatus;
import work.slhaf.partner.core.action.runner.RunnerClient;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.*;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.RepairerResult.RepairerStatus;
import work.slhaf.partner.module.modules.action.dispatcher.executor.exception.ActionExecutingFailedException;

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

    @Override
    public Void execute(ActionExecutorInput input) {
        List<ImmediateActionData> immediateActions = input.getImmediateActions();
        String userId = input.getUserId();
        for (ImmediateActionData actionData : immediateActions) {
            virtualExecutor.execute(() -> {
                if (actionData.getStatus() != ActionData.ActionStatus.PREPARE) {
                    return;
                }
                actionData.setStatus(ActionData.ActionStatus.EXECUTING);
                Map<Integer, List<MetaAction>> actionChain = actionData.getActionChain();
                List<MetaAction> virtual = new ArrayList<>();
                List<MetaAction> platform = new ArrayList<>();
                Phaser phaser = new Phaser();
                phaser.register();
                PhaserRecord phaserRecord = actionCapability.putPhaserRecord(phaser, actionData);
                List<Integer> orderList = new ArrayList<>(actionChain.keySet().stream().toList());
                orderList.sort(Integer::compareTo);
                try {
                    for (Integer order : orderList) {
                        if (actionData.getStatus().equals(ActionStatus.FAILED)) {
                            // 此时已经在 PhaserRecord 调用的 fail 方法中调整了 ActionData 的状态标记
                            // 跳出循环后仍将正常执行 phaserRecord 的移除操作
                            break;
                        }
                        List<MetaAction> metaActions = actionChain.get(order);
                        for (MetaAction metaAction : metaActions) {
                            // 根据io类型放入合适的列表
                            if (metaAction.isIo()) {
                                virtual.add(metaAction);
                            } else {
                                platform.add(metaAction);
                            }
                        }
                        // 使用phaser来承担同组的动态任务新增
                        runGroupAction(virtual, userId, actionData, virtualExecutor, phaserRecord);
                        runGroupAction(platform, userId, actionData, platformExecutor, phaserRecord);
                        phaser.arriveAndAwaitAdvance();
                        virtual.clear();
                        platform.clear();
                        // 进行行动链修正
                        CorrectorInput correctorInput = assemblyHelper.buildCorrectorInput();
                        actionCorrector.execute(correctorInput);
                    }
                } finally {
                    phaser.arriveAndDeregister();
                    actionCapability.removePhaserRecord(phaser);
                    if (actionData.getStatus() != ActionData.ActionStatus.FAILED) {
                        actionData.setStatus(ActionStatus.SUCCESS);
                    }
                }
            });

        }
        return null;

    }

    private void runGroupAction(List<MetaAction> actions, String userId, ActionData actionData,
            ExecutorService executor,
            PhaserRecord phaserRecord) {
        Phaser phaser = phaserRecord.phaser();
        phaser.bulkRegister(actions.size());
        // 不可替换为增强for，因为单组的行动单元集合数量是可以被外部干预的
        // noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < actions.size(); i++) {
            MetaAction action = actions.get(i);
            executor.execute(() -> {
                try {
                    // 两个循环需考虑最大次数，但为了达到最好融合，次数累计作用于 ActionRepairer 的修复策略选择上更合适
                    // 修复的最终结果是 action 的参数补充完整，然后能够继续行动链
                    // 如果无法补充，则该行动行动阶段可能确实有误，实际上应当在 actionRepairer 内部进行处理（行动链调整、自对话或请求用户进行干预）
                    // 所以无法补充时，行动链所属行动数据的状态需要置为 Interrupted ，等待状态变更，同时使用 Phaser 暂停(阻塞)当前行动链执行过程
                    // 这个功能应该交给 PhaserRecord 实现，尽量确保功能一致性
                    setActionParams(action, phaserRecord, userId);
                    do {
                        runnerClient.run(action);
                        MetaAction.Result result = action.getResult();
                        // 该循环对应LLM的调整参数后重试
                        if (!result.getStatus().equals(ResultStatus.SUCCESS)) {
                            // LLM决策是重构参数、执行自对话反思、还是选择向用户求助(通过cognationCore暴露方法，可能需要修改其他模块以进行适应)，仅重构参数时无需结束当前循环
                            // 若使用Phaser作为执行线程与反思、求助等调用流程的同步协调，应当需要额外维护Phaser全局字段，获取到反思结果或者用户反馈后，
                            // 调用对应的phaser注册任务，在ActionExecutor中动态添加任务至actionChain,同时启动异步执行
                            // 而且由于执行与放入的为同一个MetaAction对象，所以执行结果可被当前行动链获取，但virtual、executor两个列表似乎不行，需要重构执行模式，建议将行动链直接重构为LinkedHashMap，order为键
                            setActionParams(action, phaserRecord, userId);
                        } else {
                            break;
                        }
                        runnerClient.run(action);
                    } while (true);
                    // TODO 执行结果不再需要写入特定位置，当前的 ActionCapability
                    // 内部的行动池已经足以承担这个功能，但这也就意味着行动池或许需要考虑特殊的序列化形式避免内存占用过高,
                    // 同时也需要在某些模块执行时加上行动结果的挑取作为输入内容
                } catch (ActionExecutingFailedException e) {
                    log.error("Action executing failed: {}", action.getKey(), e);
                } finally {
                    phaser.arriveAndDeregister();
                }
            });

        }

    }

    private void setActionParams(MetaAction action, PhaserRecord phaserRecord, String userId) {
        ActionData actionData = phaserRecord.actionData();
        List<String> additionalContext = actionData.getAdditionalContext().get(actionData.getExecutingStage());
        do {
            ExtractorInput extractorInput = assemblyHelper.buildExtractorInput(action, userId, actionData,
                    additionalContext);
            ExtractorResult extractorResult = paramsExtractor.execute(extractorInput);
            if (extractorResult.isOk()) {
                action.setParams(extractorResult.getParams());
                break;
            }
            RepairerInput repairerInput = assemblyHelper.buildRepairerInput(phaserRecord, action, userId);
            RepairerResult repairerResult = actionRepairer.execute(repairerInput);
            switch (repairerResult.getStatus()) {
                // 修复成功则直接设置参数
                case RepairerStatus.OK -> additionalContext.addAll(repairerResult.getFixedData());
                // 修复失败则证明行动链不可行（外部因素，如果本身即不存在满足可能，则应当通过 ADJUST 或者 ACQUIRE 方式选择取消）
                case RepairerStatus.FAILED -> {
                    // 此处抛出执行异常，runGroupAction 为并发执行同组动作，此时只是中断了一个行动单元的执行
                    // 那么对于其他的行动单元，也需要进行中断处理，仅靠 PhaserRecord 无法完成
                    // 或许需要再增加一个集合，用于记录开启的执行线程，然后统一停止
                    // 由于行动链的并发特性，所以只需要记录单组行动单元的执行线程，但是如果此时其他的行动单元也触发了额外的线程操作
                    // （例如自对话,但此时这些触发自对话的线程本身是正常状态，会被正常中断）
                    // 也需要避免这些内容出现异常（主要是前置行动模块处针对 ActionData 的操作），应该只需要依据 FAILED 状态阻止操作即可
                    // 对于修复和动态生成的行动单元执行，都是同步操作，不再需要额外处理
                    // 但考虑到同组行动单元的执行过程，也的确用不到那么多线程中断操作，所以只要收到干预时做好拒绝策略即可
                    // 此处的话，由于主要依赖 ActionData 持有的状态防止失败行动数据继续执行，所以不再需要 phaserRecord 进行额外处理
                    // 只需要重设 ActionData 状态即可
                    actionData.setStatus(ActionData.ActionStatus.FAILED);
                    throw new ActionExecutingFailedException("行动执行失败");
                }
                // 通过自对话通道发起了干预，这里需要调用 phaserRecord 进行一次阻塞
                // 如果通过 phaserRecord 进行阻塞，那么在前置模块的 InterventionHandler 需要额外得知当前 ActionData
                // 的内容，这点是可以做到的
                // 如果在 ActionRepairer 内部调用阻塞，还是无法免除同样的逻辑，即 RepairerResult 内容需要携带干预信息，但这些内容最终是在
                // ActionData 中放置的，相当于绕了一层，不太合适
                case RepairerStatus.ACQUIRE -> {
                    phaserRecord.interrupt();
                }
            }
        } while (true);
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    private class AssemblyHelper {

        private AssemblyHelper() {
        }

        private RepairerInput buildRepairerInput(PhaserRecord phaserRecord, MetaAction action, String userId) {
            RepairerInput input = new RepairerInput();
            MetaActionInfo metaActionInfo = actionCapability.loadMetaActionInfo(action.getKey());
            ActionData actionData = phaserRecord.actionData();
            input.setHistoryActionResults(getHistoryActionResults(actionData));
            input.setParams(metaActionInfo.getParams());
            input.setRecentMessages(cognationCapability.getChatMessages());
            input.setActionDescription(metaActionInfo.getDescription());
            input.setUserId(userId);
            input.setPhaserRecord(phaserRecord);
            return input;
        }

        private ExtractorInput buildExtractorInput(MetaAction action, String userId, ActionData actionData,
                List<String> additionalContext) {
            ExtractorInput input = new ExtractorInput();
            input.setEvaluatedSlices(memoryCapability.getActivatedSlices(userId));
            input.setRecentMessages(cognationCapability.getChatMessages());
            input.setMetaActionInfo(actionCapability.loadMetaActionInfo(action.getKey()));
            input.setHistoryActionResults(getHistoryActionResults(actionData));
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
