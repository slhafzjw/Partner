package work.slhaf.partner.module.common.module;

import work.slhaf.partner.api.agent.factory.module.abstracts.AbstractAgentRunningModule;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

public abstract class PostRunningAbstractAgentModuleAbstract extends AbstractAgentRunningModule<PartnerRunningFlowContext> {

    @Override
    public final void execute(PartnerRunningFlowContext context) {
        boolean trigger = context.getModuleContext().getExtraContext().getBoolean("post_process_trigger");
        if (!trigger && relyOnMessage()) {
            return;
        }
        doExecute(context);
    }

    public abstract void doExecute(PartnerRunningFlowContext context);

    protected abstract boolean relyOnMessage();
}
