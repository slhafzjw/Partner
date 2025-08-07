package work.slhaf.demo.service.capability;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;

@Capability("test_a")
public interface ATestCapability {
    void testMethodNormalA();
    String testMethodNormalB();
}
