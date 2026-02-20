package work.slhaf.partner.module.common.module;

import work.slhaf.partner.api.agent.factory.module.abstracts.AbstractAgentModule;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

public abstract class PostRunningAbstractAgentModuleAbstract extends AbstractAgentModule.Running<PartnerRunningFlowContext> {
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
