package work.slhaf.demo.service.core;

import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;

@CapabilityCore("test_b")
public class BTestCore {

    @CapabilityMethod
    public void testMethodNormalA() {
        System.out.println("BTestCore::testMethodNormalA");
    }

    public String testCoordinateSubMethod() {
        return "BTestCore::testMethodCoordinate";
    }
}
