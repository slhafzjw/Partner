package work.slhaf.partner.module.modules.action.dispatcher.scheduler;

import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.core.action.entity.ScheduledActionData;

import java.util.List;

@AgentSubModule
public class ActionScheduler extends AgentRunningSubModule<List<ScheduledActionData>, Void> {
    @Override
    public Void execute(List<ScheduledActionData> data) {

        return null;
    }
}
