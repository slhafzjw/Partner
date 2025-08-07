package module;

import work.slhaf.partner.api.agent.flow.abstracts.AgentInteractionModule;
import work.slhaf.partner.api.agent.flow.entity.InteractionFlowContext;

public class MyAgentInteractionModule extends AgentInteractionModule {
    @Override
    public void execute(InteractionFlowContext context) {
        System.out.println("MyAgentInteractionModule");
    }
}
