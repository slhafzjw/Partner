package work.slhaf.demo.capability.module;

import work.slhaf.demo.capability.CapabilityRegisterFactory;

public abstract class CapabilityHolder {
    protected CapabilityHolder(){
        CapabilityRegisterFactory.getInstance().registerModule(this);
    }
}
