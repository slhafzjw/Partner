package work.slhaf.partner.module.impression;

import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.module.communication.AfterRolling;
import work.slhaf.partner.module.communication.RollingResult;

public class ImpressionUpdater extends AbstractAgentModule.Standalone implements AfterRolling {

    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    public void consume(RollingResult result) {

    }

}
