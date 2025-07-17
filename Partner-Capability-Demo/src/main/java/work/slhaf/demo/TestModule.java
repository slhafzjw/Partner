package work.slhaf.demo;

import work.slhaf.demo.ability.CacheCapability;
import work.slhaf.demo.ability.MemoryCapability;
import work.slhaf.demo.capability.annotation.InjectCapability;
import work.slhaf.demo.capability.module.CapabilityHolder;

public class TestModule extends CapabilityHolder {
    @InjectCapability
    private MemoryCapability capability;

    public void execute(){
        System.out.println("111");
        System.out.println(capability.selectMemory("zjw"));
    }
}
