package work.slhaf.partner.module.common.module;

import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

public abstract class PostRunningModule extends AgentRunningModule<PartnerRunningFlowContext> {

    @Override
    public final void execute(PartnerRunningFlowContext context) {
        boolean trigger = context.getModuleContext().getExtraContext().getBoolean("post_process_trigger");
        if (!trigger) {
            return;
        }
        doExecute(context);
    }

    public abstract void doExecute(PartnerRunningFlowContext context);

}
