package work.slhaf.partner;

import work.slhaf.partner.common.vector.VectorClientRegistry;
import work.slhaf.partner.framework.agent.Agent;
import work.slhaf.partner.runtime.gateway.WebSocketGatewayRegistration;

public class Main {
    public static void main(String[] args) {
        Agent.newAgent(Main.class)
                .addGatewayRegistration(WebSocketGatewayRegistration.INSTANCE)
                .addConfigurable(new VectorClientRegistry())
                .launch();
    }
}
