package module;

import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.entity.RunningFlowContext;

public class MyAgentRunningModule extends AgentRunningModule {
    @Override
    public void execute(RunningFlowContext context) {
        System.out.println("MyAgentRunningModule");
    }
}
