package work.slhaf.partner.module.modules.action.dispatcher;

import lombok.val;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentRunningModule;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.ExecutableAction;
import work.slhaf.partner.core.action.entity.ImmediateExecutableAction;
import work.slhaf.partner.core.action.entity.SchedulableExecutableAction;
import work.slhaf.partner.module.common.module.PostRunningAbstractAgentModuleAbstract;
import work.slhaf.partner.module.modules.action.dispatcher.executor.ActionExecutor;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.ActionExecutorInput;
import work.slhaf.partner.module.modules.action.dispatcher.scheduler.ActionScheduler;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

@AgentRunningModule(name = "action_dispatcher", order = 7)
public class ActionDispatcher extends PostRunningAbstractAgentModuleAbstract {

    @InjectCapability
    private ActionCapability actionCapability;

    @InjectModule
    private ActionExecutor actionExecutor;
    @InjectModule
    private ActionScheduler actionScheduler;

    private ExecutorService executor;

    @Init
    public void init() {
        executor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
    }

    @Override
    public void doExecute(PartnerRunningFlowContext context) {
        // 只需要处理prepared action，因为pending action在用户确认后也将变为prepared action
        // 将PLANNING action放入时间轮中，IMMEDIATE action直接进入并发执行流
        // 对于将触发的PLANNING
        // action，理想做法是将执行工具做成执行链的形式，模型的自对话流程、是否通知用户都做成与普通工具同等的通用可选能力，避免绑定固定流程
        executor.execute(() -> {
            String userId = context.getUserId();
            val preparedActions = actionCapability.listActions(ExecutableAction.Status.PREPARE, userId);
            // 分类成PLANNING和IMMEDIATE两类
            Set<SchedulableExecutableAction> scheduledActions = new HashSet<>();
            Set<ImmediateExecutableAction> immediateActions = new HashSet<>();
            for (ExecutableAction preparedAction : preparedActions) {
                if (preparedAction instanceof SchedulableExecutableAction actionInfo) {
                    scheduledActions.add(actionInfo);
                } else if (preparedAction instanceof ImmediateExecutableAction actionInfo) {
                    immediateActions.add(actionInfo);
                }
            }
            actionExecutor.execute(new ActionExecutorInput(immediateActions));
            actionScheduler.execute(scheduledActions);
        });
    }

    @Override
    protected boolean relyOnMessage() {
        return false;
    }

}
