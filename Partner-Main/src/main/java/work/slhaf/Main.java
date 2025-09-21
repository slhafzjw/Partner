package work.slhaf;

import work.slhaf.partner.api.agent.Agent;
import work.slhaf.partner.common.config.PartnerAgentConfigManager;
import work.slhaf.partner.runtime.exception.PartnerExceptionCallback;
import work.slhaf.partner.runtime.interaction.WebSocketGateway;

public class Main {
    public static void main(String[] args) {
        Agent.newAgent(Main.class)
                .setAgentConfigManager(PartnerAgentConfigManager.class)
                .setGateway(WebSocketGateway.class)
                .setAgentExceptionCallback(PartnerExceptionCallback.class)
                .launch();
    }
}