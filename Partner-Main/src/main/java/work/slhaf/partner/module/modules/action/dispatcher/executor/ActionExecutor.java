package work.slhaf.partner.module.modules.action.dispatcher.executor;

import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.entity.ImmediateActionData;

import java.util.List;

@AgentSubModule
public class ActionExecutor extends AgentRunningSubModule<List<ImmediateActionData>, Void> {

    @InjectCapability
    private ActionCapability actionCapability;

    @Override
    public Void execute(List<ImmediateActionData> immediateActions) {

        return null;
    }
}
