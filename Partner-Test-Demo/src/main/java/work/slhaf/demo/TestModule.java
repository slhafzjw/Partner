package work.slhaf.demo;

import work.slhaf.demo.ability.MemoryCapability;
import work.slhaf.partner.api.capability.annotation.CapabilityHolder;
import work.slhaf.partner.api.capability.annotation.InjectCapability;

@CapabilityHolder
public class TestModule  {
    @InjectCapability
    private MemoryCapability capability;

    public void execute(){
        System.out.println("111");
        System.out.println(capability.selectMemory("zjw"));
    }
}
