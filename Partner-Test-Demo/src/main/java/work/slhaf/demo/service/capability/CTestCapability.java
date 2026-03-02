package work.slhaf.demo.service.capability;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;

@Capability("test_c")
public interface CTestCapability {
    void testMethodNormalA(String input);

}
