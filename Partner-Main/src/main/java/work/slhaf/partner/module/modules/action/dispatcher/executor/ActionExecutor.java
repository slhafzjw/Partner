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
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

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
            List<MetaAction> actionChain = actionData.getActionChain();
            actionChain.sort(MetaAction::compareTo);
            List<MetaAction> virtual = new ArrayList<>();
            List<MetaAction> platform = new ArrayList<>();
            int order;
            for (int index = 0; index < actionChain.size(); index++) {
                MetaAction metaAction = actionChain.get(index);
                // 根据io类型放入合适的列表
                if (metaAction.isIo()) {
                    virtual.add(metaAction);
                } else {
                    platform.add(metaAction);
                }
                // 记录当前order
                order = metaAction.getOrder();
                // 如果下一个行动单元的order与当前不同，则执行并清空当前组内容
                if (actionChain.size() <= (index + 1) || actionChain.get(index + 1).getOrder() != order) {
                    runGroupAction(virtual, platform, actionChain);
                }
            }
        });
    }

    //TODO 考虑是否使用phaser来承担同组的动态任务新增
    private void runGroupAction(List<MetaAction> virtual, List<MetaAction> platform, List<MetaAction> actionChain) {
        boolean first = true;
        do {
            CountDownLatch latch = new CountDownLatch(virtual.size() + platform.size());
            runGroupAction(virtual, virtualExecutor, actionChain, latch, first);
            runGroupAction(platform, platformExecutor, actionChain, latch, first);
            try {
                latch.await();
            } catch (InterruptedException e) {
                log.error("[{}] CountDownLatch被中断", modelKey());
            }
            first = false;
        } while (!virtual.isEmpty() || !platform.isEmpty());
    }

    private void runGroupAction(List<MetaAction> actions, ExecutorService executor, List<MetaAction> actionChain, CountDownLatch latch, boolean first) {
        if (!first && !new HashSet<>(actionChain).containsAll(actions)) {
            // 该部分对应LLM新增本组执行单元时，将其添加至actionChain记录。对于后续组级别的新增，将直接在上一级调用处体现，除了注意并发安全外无需额外处理
            int index = actionChain.indexOf(actions.getLast());
            actionChain.addAll(index, actions);
        }
        for (MetaAction action : actions) {
            executor.execute(() -> {
                boolean success = true;
                do {
                    // 该循环对应LLM的调整参数后重试
                    if (!success) {
                        //TODO LLM决策是重构参数、执行自对话反思、还是选择向用户求助(通过cognationCore暴露方法，可能需要修改其他模块以进行适应)

                    }
                    action.run();
                    success = action.getResult().isSuccess();
                } while (!success);
                latch.countDown();
                //TODO 将执行结果写入特定对话角色记忆(cognationCore暴露方法)
            });
        }
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
