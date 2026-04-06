package work.slhaf.partner;

import work.slhaf.partner.common.config.PartnerAgentConfigLoader;
import work.slhaf.partner.framework.agent.Agent;
import work.slhaf.partner.runtime.exception.PartnerExceptionCallback;
import work.slhaf.partner.runtime.interaction.WebSocketGateway;

public class Main {
    public static void main(String[] args) {
        Agent.newAgent(Main.class)
                .setAgentConfigManager(PartnerAgentConfigLoader.class)
                .setGateway(WebSocketGateway.class)
                .setAgentExceptionCallback(PartnerExceptionCallback.class)
                .launch();
    }
}