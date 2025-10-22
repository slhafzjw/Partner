package work.slhaf.partner.module.modules.action.dispatcher.scheduler;

import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.core.action.entity.ScheduledActionInfo;

import java.util.List;

@AgentSubModule
public class ActionScheduler extends AgentRunningSubModule<List<ScheduledActionInfo>, Void> {
    @Override
    public Void execute(List<ScheduledActionInfo> data) {

        return null;
    }
}
