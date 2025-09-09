package work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts;

import work.slhaf.partner.api.agent.runtime.interaction.flow.entity.RunningFlowContext;

import java.io.IOException;

/**
 * 流程执行模块基类
 */
public abstract class AgentRunningModule<C extends RunningFlowContext> extends Module {
    public abstract void execute(C context) throws IOException, ClassNotFoundException;
}
