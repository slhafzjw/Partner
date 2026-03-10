package work.slhaf.partner.core.perceive;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;

@Capability(value = "perceive")
public interface PerceiveCapability {
    String refreshInteract();

    long showLastInteract();
}
