package work.slhaf.partner.api.capability.module;

import work.slhaf.partner.api.capability.CapabilityRegisterFactory;

public abstract class CapabilityHolder {
    protected CapabilityHolder() {
        CapabilityRegisterFactory.getInstance().registerModule(this);
    }
}
