package work.slhaf.demo;


import work.slhaf.partner.api.agent.Agent;
import work.slhaf.partner.api.agent.runtime.interaction.AgentGateway;
import work.slhaf.partner.api.agent.runtime.interaction.AgentInteractionAdapter;
import work.slhaf.partner.api.agent.runtime.interaction.data.AgentInputData;
import work.slhaf.partner.api.agent.runtime.interaction.data.AgentOutputData;
import work.slhaf.partner.api.agent.runtime.interaction.flow.entity.RunningFlowContext;

public class AgentDemoApplication {
    public static void main(String[] args) {
        Agent.newAgent(AgentDemoApplication.class).setGateway(new AgentGateway() {
            @Override
            public void launch() {

            }

            @Override
            public <I extends AgentInputData, O extends AgentOutputData, C extends RunningFlowContext> AgentInteractionAdapter<I, O, C> adapter() {
                return null;
            }
        }).launch();
    }
}