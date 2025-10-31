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
import java.util.LinkedHashMap;
import java.util.List;
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
            LinkedHashMap<Integer, List<MetaAction>> actionChain = actionData.getActionChain();
            List<MetaAction> virtual = new ArrayList<>();
            List<MetaAction> platform = new ArrayList<>();
            Phaser phaser = new Phaser();
            phaser.register();
            actionCapability.putPhaserRecord(phaser, actionData);
            actionChain.forEach((k, v) -> {
                for (MetaAction metaAction : v) {
                    // 根据io类型放入合适的列表
                    if (metaAction.isIo()) {
                        virtual.add(metaAction);
                    } else {
                        platform.add(metaAction);
                    }
                }
                runGroupAction(virtual, platform, actionChain, phaser);
                virtual.clear();
                platform.clear();
                phaser.arriveAndAwaitAdvance();
            });
            actionCapability.removePhaserRecord(phaser);
        });

    }

    // 使用phaser来承担同组的动态任务新增
    private void runGroupAction(List<MetaAction> virtual, List<MetaAction> platform, LinkedHashMap<Integer, List<MetaAction>> actionChain, Phaser phaser) {
        runGroupAction(virtual, virtualExecutor, phaser);
        runGroupAction(platform, platformExecutor, phaser);
    }

    private void runGroupAction(List<MetaAction> actions, ExecutorService executor, Phaser phaser) {
        for (MetaAction action : actions) {
            phaser.register();
            executor.execute(() -> {
                try {
                    MetaAction.Result result = action.getResult();
                    do {
                        // 该循环对应LLM的调整参数后重试
                        if (!result.isSuccess()) {
                            //TODO LLM决策是重构参数、执行自对话反思、还是选择向用户求助(通过cognationCore暴露方法，可能需要修改其他模块以进行适应)，仅重构参数时无需结束当前循环
                            // 若使用Phaser作为执行线程与反思、求助等调用流程的同步协调，应当需要额外维护Phaser全局字段，获取到反思结果或者用户反馈后，
                            // 调用对应的phaser注册任务，在ActionExecutor中动态添加任务至actionChain,同时启动异步执行
                            // 而且由于执行与放入的为同一个MetaAction对象，所以执行结果可被当前行动链获取，但virtual、executor两个列表似乎不行，需要重构执行模式，建议将行动链直接重构为LinkedHashMap，order为键
                            String input = getInput(result.getData());

                        }
                        action.run();
                    } while (!result.isSuccess());
                    //TODO 将执行结果写入特定对话角色记忆(cognationCore暴露方法)
                } finally {
                    phaser.arriveAndDeregister();
                }
            });
        }
    }

    private String getInput(String data) {

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
