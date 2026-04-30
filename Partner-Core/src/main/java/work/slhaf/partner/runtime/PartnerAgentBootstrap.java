package work.slhaf.partner.runtime;

import work.slhaf.partner.common.vector.VectorClientRegistry;
import work.slhaf.partner.framework.agent.Agent;
import work.slhaf.partner.runtime.gateway.WebSocketGatewayRegistration;

public final class PartnerAgentBootstrap extends Agent.AgentBootstrap {

    public PartnerAgentBootstrap(Agent.AgentApp agentApp) {
        super(agentApp);
    }

    @Override
    protected void bootstrap() {
        addGatewayRegistration(WebSocketGatewayRegistration.INSTANCE);
        addConfigurable(new VectorClientRegistry());
    }
}
