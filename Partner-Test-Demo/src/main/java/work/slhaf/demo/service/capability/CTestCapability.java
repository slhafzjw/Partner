package work.slhaf.demo.service.capability;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.api.agent.factory.capability.annotation.ToCoordinated;

@Capability("test_c")
public interface CTestCapability {
    void testMethodNormalA(String input);

    @ToCoordinated
    void testMethodCoordinate(String input);
}
