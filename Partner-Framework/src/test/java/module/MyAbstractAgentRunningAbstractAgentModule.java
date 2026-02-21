package module;

import work.slhaf.partner.api.agent.runtime.interaction.flow.entity.RunningFlowContext;

public class MyAbstractAgentRunningAbstractAgentModule extends AbstractAgentRunningModule {
    @Override
    public void execute(RunningFlowContext context) {
        System.out.println("MyAbstractAgentRunningAbstractAgentModule");
    }
}
