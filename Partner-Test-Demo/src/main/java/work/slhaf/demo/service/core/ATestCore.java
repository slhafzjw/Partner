package work.slhaf.demo.service.core;

import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;

@CapabilityCore("test_a")
public class ATestCore {

    @CapabilityMethod
    public void testMethodNormalA() {
        System.out.println("ATestCore::testMethodNormalA");
    }

    @CapabilityMethod
    public String testMethodNormalB() {
        return "ATestCore::testMethodNormalB";
    }

}
