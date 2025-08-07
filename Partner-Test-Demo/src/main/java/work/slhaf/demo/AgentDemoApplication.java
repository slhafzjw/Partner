package work.slhaf.demo;


import work.slhaf.demo.flow.AgentDemoFlowContext;
import work.slhaf.partner.api.agent.Agent;

public class AgentDemoApplication {
    public static void main(String[] args) {
        Agent.newAgent(AgentDemoApplication.class, new AgentDemoFlowContext())
                .run();
    }
}