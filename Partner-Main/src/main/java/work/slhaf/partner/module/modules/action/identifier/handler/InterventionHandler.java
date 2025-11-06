package work.slhaf.partner.module.modules.action.identifier.handler;

import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.module.modules.action.identifier.handler.entity.HandlerInput;

@AgentSubModule
public class InterventionHandler extends AgentRunningSubModule<HandlerInput,Void> {

    @Override
    public Void execute(HandlerInput data) {

        return null;
    }
    
}
