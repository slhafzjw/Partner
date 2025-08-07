package work.slhaf.demo.service;

import work.slhaf.demo.service.core.BTestCore;
import work.slhaf.demo.service.core.CTestCore;
import work.slhaf.partner.api.agent.factory.capability.annotation.CoordinateManager;
import work.slhaf.partner.api.agent.factory.capability.annotation.Coordinated;

@CoordinateManager
public class TestCoordinateManager {
    private BTestCore bTestCore = new BTestCore();
    private CTestCore cTestCore = new CTestCore();

    @Coordinated(capability = "test_c")
    public void testMethodCoordinate(String input){
        String resultB = bTestCore.testCoordinateSubMethod();
        cTestCore.testCoordinateSubMethod(resultB);
    }
}
