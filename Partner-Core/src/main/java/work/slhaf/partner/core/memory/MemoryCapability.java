package work.slhaf.partner.core.memory;

import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.framework.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.framework.agent.model.pojo.Message;

import java.util.Collection;
import java.util.List;

@Capability(value = "memory")
public interface MemoryCapability {

    MemoryUnit getMemoryUnit(String unitId);

    MemorySlice getMemorySlice(String unitId, String sliceId);

    MemoryUnit updateMemoryUnit(List<Message> chatMessages, String summary);

    Collection<MemoryUnit> listMemoryUnits();

    void refreshMemorySession();

    String getMemorySessionId();

}
