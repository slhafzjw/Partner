package work.slhaf.partner.module.modules.perceive.selector;

import lombok.Setter;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

@Setter
public class PerceiveSelector extends AbstractAgentModule.Running<PartnerRunningFlowContext> {
    @InjectCapability
    private PerceiveCapability perceiveCapability;

    @Override
    public void execute(PartnerRunningFlowContext context) {
    }

    @Override
    public int order() {
        return 2;
    }
}
