package work.slhaf.partner.api.agent.flow.abstracts;

import work.slhaf.partner.api.agent.flow.entity.RunningFlowContext;

/**
 * 流程执行模块基类
 */
public abstract class AgentRunningModule extends Module {
    public abstract void execute(RunningFlowContext context);
}
