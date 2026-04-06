package work.slhaf.partner.core.perceive;

import work.slhaf.partner.framework.agent.factory.capability.annotation.Capability;

import java.time.Instant;

@Capability(value = "perceive")
public interface PerceiveCapability {
    String refreshInteract();

    Instant showLastInteract();
}
