package work.slhaf.partner.module.modules.action.dispatcher;

import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.core.action.entity.ImmediateActionData;
import work.slhaf.partner.core.action.entity.ScheduledActionData;
import work.slhaf.partner.module.common.module.PostRunningModule;
import work.slhaf.partner.module.modules.action.dispatcher.executor.ActionExecutor;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.ActionExecutorInput;
import work.slhaf.partner.module.modules.action.dispatcher.scheduler.ActionScheduler;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

@AgentModule(name = "action_dispatcher", order = 7)
public class ActionDispatcher extends PostRunningModule {

    @InjectCapability
    private ActionCapability actionCapability;

    @InjectModule
    private ActionExecutor actionExecutor;
    @InjectModule
    private ActionScheduler actionScheduler;

    private ExecutorService executor;
    private final AssemblyHelper assemblyHelper = new AssemblyHelper();

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
            List<ActionData> preparedActions = actionCapability.listPreparedAction(userId);
            // 分类成PLANNING和IMMEDIATE两类
            List<ScheduledActionData> scheduledActions = new ArrayList<>();
            List<ImmediateActionData> immediateActions = new ArrayList<>();
            for (ActionData preparedAction : preparedActions) {
                if (preparedAction instanceof ScheduledActionData actionInfo) {
                    scheduledActions.add(actionInfo);
                } else if (preparedAction instanceof ImmediateActionData actionInfo) {
                    immediateActions.add(actionInfo);
                }
            }
            actionExecutor.execute(assemblyHelper.buildExecutorInput(immediateActions, userId));
            actionScheduler.execute(scheduledActions);
        });
    }

    @Override
    protected boolean relyOnMessage() {
        return false;
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    private class AssemblyHelper {

        public ActionExecutorInput buildExecutorInput(List<ImmediateActionData> immediateActions, String userId) {
            ActionExecutorInput input = new ActionExecutorInput();
            input.setImmediateActions(immediateActions);
            input.setUserId(userId);
            return input;
        }

    }
}
