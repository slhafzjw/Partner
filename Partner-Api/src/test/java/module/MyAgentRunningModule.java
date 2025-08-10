package module;

import work.slhaf.partner.api.agent.flow.abstracts.AgentRunningModule;
import work.slhaf.partner.api.agent.flow.entity.RunningFlowContext;

public class MyAgentRunningModule extends AgentRunningModule {
    @Override
    public void execute(RunningFlowContext context) {
        System.out.println("MyAgentRunningModule");
    }
}
