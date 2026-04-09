package work.slhaf.partner;

import work.slhaf.partner.framework.agent.Agent;
import work.slhaf.partner.runtime.interaction.WebSocketGatewayRegistration;

public class Main {
    public static void main(String[] args) {
        Agent.newAgent(Main.class)
                .addGatewayRegistration(WebSocketGatewayRegistration.INSTANCE)
                .launch();
    }
}
