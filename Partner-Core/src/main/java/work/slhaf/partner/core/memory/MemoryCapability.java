package work.slhaf.partner.core.memory;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;

import java.util.Collection;

@Capability(value = "memory")
public interface MemoryCapability {

    void saveMemoryUnit(MemoryUnit memoryUnit);

    MemoryUnit getMemoryUnit(String unitId);

    MemorySlice getMemorySlice(String unitId, String sliceId);

    Collection<MemoryUnit> listMemoryUnits();

    void refreshMemorySession();

    String getMemorySessionId();

}
