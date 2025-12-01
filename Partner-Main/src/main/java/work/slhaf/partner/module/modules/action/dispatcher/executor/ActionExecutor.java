package work.slhaf.partner.module.modules.action.dispatcher.executor;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.core.action.entity.ImmediateActionData;
import work.slhaf.partner.core.action.entity.MetaAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;

@Slf4j
@AgentSubModule
public class ActionExecutor extends AgentRunningSubModule<List<ImmediateActionData>, Void> implements ActivateModel {

    @InjectCapability
    private ActionCapability actionCapability;

    private ExecutorService virtualExecutor;
    private ExecutorService platformExecutor;

    @Init
    public void init() {
        virtualExecutor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
        platformExecutor = actionCapability.getExecutor(ActionCore.ExecutorType.PLATFORM);
    }

    @Override
    public Void execute(List<ImmediateActionData> immediateActions) {
        for (ImmediateActionData actionData : immediateActions) {
            handleActionData(actionData);
        }
        return null;
    }

    private void handleActionData(ImmediateActionData actionData) {
        virtualExecutor.execute(() -> {
            actionData.setStatus(ActionData.ActionStatus.EXECUTING);
            Map<Integer, List<MetaAction>> actionChain = actionData.getActionChain();
            List<MetaAction> virtual = new ArrayList<>();
            List<MetaAction> platform = new ArrayList<>();
            Phaser phaser = new Phaser();
            phaser.register();
            actionCapability.putPhaserRecord(phaser, actionData);
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
                    runGroupAction(virtual, platform, phaser);
                    phaser.arriveAndAwaitAdvance();
                    virtual.clear();
                    platform.clear();
                }
            } finally {
                phaser.arriveAndDeregister();
                actionCapability.removePhaserRecord(phaser);
            }
        });

    }

    // 使用phaser来承担同组的动态任务新增
    private void runGroupAction(List<MetaAction> virtual, List<MetaAction> platform,
                                Phaser phaser) {
        runGroupAction(virtual, virtualExecutor, phaser);
        runGroupAction(platform, platformExecutor, phaser);
    }

    private void runGroupAction(List<MetaAction> actions, ExecutorService executor, Phaser phaser) {
        phaser.bulkRegister(actions.size());
        for (MetaAction action : actions) {
            executor.execute(() -> {
                try {
                    //TODO 使用 LLM 填充行动参数信息

                    actionCapability.execute(action);
                    MetaAction.Result result = action.getResult();
                    do {
                        // 该循环对应LLM的调整参数后重试
                        if (!result.isSuccess()) {
                            // LLM决策是重构参数、执行自对话反思、还是选择向用户求助(通过cognationCore暴露方法，可能需要修改其他模块以进行适应)，仅重构参数时无需结束当前循环
                            // 若使用Phaser作为执行线程与反思、求助等调用流程的同步协调，应当需要额外维护Phaser全局字段，获取到反思结果或者用户反馈后，
                            // 调用对应的phaser注册任务，在ActionExecutor中动态添加任务至actionChain,同时启动异步执行
                            // 而且由于执行与放入的为同一个MetaAction对象，所以执行结果可被当前行动链获取，但virtual、executor两个列表似乎不行，需要重构执行模式，建议将行动链直接重构为LinkedHashMap，order为键
                            String input = buildFixInput(result.getData());
                            // 执行时不可使用`for in`和`forEach`，因为在`Intervention`相关模块存在动态调整
                        }
                        actionCapability.execute(action);
                    } while (!result.isSuccess());
                    // TODO 将执行结果写入特定对话角色记忆(cognationCore暴露方法)

                } finally {
                    phaser.arriveAndDeregister();
                }
            });
        }
    }

    private String buildFixInput(String data) {

        return null;
    }

    @Override
    public String modelKey() {
        return "action_executor";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }

}
