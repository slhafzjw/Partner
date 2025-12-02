package work.slhaf.partner.module.modules.action.dispatcher.executor;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.core.action.entity.ImmediateActionData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.PhaserRecord;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.*;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.RepairerResult.RepairerStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;

@Slf4j
@AgentSubModule
public class ActionExecutor extends AgentRunningSubModule<List<ImmediateActionData>, Void> {

    @InjectCapability
    private ActionCapability actionCapability;

    @InjectModule
    private ParamsExtractor paramsExtractor;
    @InjectModule
    private ActionRepairer actionRepairer;
    @InjectModule
    private ActionCorrector actionCorrector;

    private ExecutorService virtualExecutor;
    private ExecutorService platformExecutor;

    private final AssemblyHelper assemblyHelper = new AssemblyHelper();

    @Init
    public void init() {
        virtualExecutor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
        platformExecutor = actionCapability.getExecutor(ActionCore.ExecutorType.PLATFORM);
    }

    @Override
    public Void execute(List<ImmediateActionData> immediateActions) {
        for (ImmediateActionData actionData : immediateActions) {
            virtualExecutor.execute(() -> {
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
                        runGroupAction(virtual, virtualExecutor, phaserRecord);
                        runGroupAction(platform, platformExecutor, phaserRecord);
                        phaser.arriveAndAwaitAdvance();
                        virtual.clear();
                        platform.clear();
                    }
                } finally {
                    CorrectorInput correctorInput = assemblyHelper.buildCorrectorInput();
                    actionCorrector.execute(correctorInput);
                    phaser.arriveAndDeregister();
                    actionCapability.removePhaserRecord(phaser);
                }
            });
        }
        return null;
    }

    private void runGroupAction(List<MetaAction> actions, ExecutorService executor, PhaserRecord phaserRecord) {
        Phaser phaser = phaserRecord.phaser();
        phaser.bulkRegister(actions.size());
        //不可替换为增强for，因为单组的行动单元集合数量是可以被外部干预的
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < actions.size(); i++) {
            MetaAction action = actions.get(i);
            executor.execute(() -> {
                try {
                    ExtractorInput extractorInput = assemblyHelper.buildExtractorInput();
                    ExtractorResult extractorResult = paramsExtractor.execute(extractorInput);
                    // 两个循环需考虑最大次数，但为了达到最好融合，次数累计作用于 ActionRepairer 的修复策略选择上更合适
                    if (!extractorResult.isOk()) {
                        // 修复的最终结果是 action 的参数补充完整，然后能够继续行动链
                        // 如果无法补充，则该行动行动阶段可能确实有误，实际上应当在 actionRepairer 内部进行处理（行动链调整、自对话或请求用户进行干预）
                        // 所以无法补充时，行动链所属行动数据的状态需要置为 Interrupted ，等待状态变更，同时使用 Phaser 暂停(阻塞)当前行动链执行过程
                        // 这个功能应该交给 PhaserRecord 实现，尽量确保功能一致性
                        repairActionParams(action, phaserRecord);
                    }
                    do {
                        actionCapability.execute(action);
                        MetaAction.Result result = action.getResult();
                        // 该循环对应LLM的调整参数后重试
                        if (!result.isSuccess()) {
                            // LLM决策是重构参数、执行自对话反思、还是选择向用户求助(通过cognationCore暴露方法，可能需要修改其他模块以进行适应)，仅重构参数时无需结束当前循环
                            // 若使用Phaser作为执行线程与反思、求助等调用流程的同步协调，应当需要额外维护Phaser全局字段，获取到反思结果或者用户反馈后，
                            // 调用对应的phaser注册任务，在ActionExecutor中动态添加任务至actionChain,同时启动异步执行
                            // 而且由于执行与放入的为同一个MetaAction对象，所以执行结果可被当前行动链获取，但virtual、executor两个列表似乎不行，需要重构执行模式，建议将行动链直接重构为LinkedHashMap，order为键
                            repairActionParams(action, phaserRecord);
                        } else {
                            break;
                        }
                        actionCapability.execute(action);
                    } while (true);
                    //TODO 执行结果不再需要写入特定位置，当前的 ActionCapability 内部的行动池已经足以承担这个功能，但这也就意味着行动池或许需要考虑特殊的序列化形式避免内存占用过高，同时也需要在某些模块执行时加上行动结果的挑取作为输入内容
                } finally {
                    phaser.arriveAndDeregister();
                }
            });
        }
    }

    private void repairActionParams(MetaAction action, PhaserRecord phaserRecord) {
        do {
            RepairerInput repairerInput = assemblyHelper.buildRepairerInput();
            RepairerResult repairerResult = actionRepairer.execute(repairerInput);
            switch (repairerResult.getStatus()) {
                // 修复成功则直接设置参数
                case RepairerStatus.OK -> action.setParams(repairerResult.getParams());
                // 修复失败则证明行动链不可行（外部因素，如果本身即不存在满足可能，则应当通过 ADJUST 或者 ACQUIRE 方式选择取消）
                case RepairerStatus.FAILED -> phaserRecord.fail();
                // 按照逻辑设定，这里将不可能步入这个分支，除非 ActionRepairer 逻辑有误
                case RepairerStatus.ACQUIRE -> {
                    phaserRecord.interrupt();
                    continue;
                }
            }
            break;
        } while (true);
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    private class AssemblyHelper {

        private AssemblyHelper() {
        }

        public RepairerInput buildRepairerInput() {
            return null;
        }

        public ExtractorInput buildExtractorInput() {
            return null;
        }

        public CorrectorInput buildCorrectorInput() {
            return null;
        }
    }

}
