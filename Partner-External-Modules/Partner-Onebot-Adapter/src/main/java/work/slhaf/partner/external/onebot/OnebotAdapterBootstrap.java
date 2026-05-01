package work.slhaf.partner.external.onebot;

import work.slhaf.partner.external.onebot.gateway.OnebotGatewayRegistration;
import work.slhaf.partner.framework.agent.Agent;

public class OnebotAdapterBootstrap extends Agent.AgentBootstrap{

    protected OnebotAdapterBootstrap(Agent.AgentApp agentApp) {
        super(agentApp);
    }

    @Override
    protected void bootstrap() {
        addGatewayRegistration(new OnebotGatewayRegistration());
    }
}
