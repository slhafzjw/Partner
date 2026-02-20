package work.slhaf.demo.service.core;

import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;

@CapabilityCore("test_c")
public class CTestCore {

    @CapabilityMethod
    public void testMethodNormalA(String input) {
        System.out.println("CTestCore::testMethodNormalA, input: " + input);
    }

    public void testCoordinateSubMethod(String input) {
        System.out.println("CTestCore::testMethodCoordinate, input: " + input);
    }
}
