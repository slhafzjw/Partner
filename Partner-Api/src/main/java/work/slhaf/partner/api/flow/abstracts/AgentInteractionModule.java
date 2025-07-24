package work.slhaf.partner.api.flow.abstracts;

import work.slhaf.partner.api.flow.entity.InteractionFlowContext;

/**
 * 流程执行模块基类
 */
public abstract class AgentInteractionModule extends Module {
    public abstract void execute(InteractionFlowContext context);
}
