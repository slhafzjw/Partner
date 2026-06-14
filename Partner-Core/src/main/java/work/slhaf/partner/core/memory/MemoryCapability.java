package work.slhaf.partner.core.memory;

import work.slhaf.partner.core.memory.pojo.MemorySliceSnapshot;
import work.slhaf.partner.core.memory.pojo.MemoryUnitSnapshot;
import work.slhaf.partner.framework.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;

import java.util.Collection;
import java.util.List;

@Capability(value = "memory")
public interface MemoryCapability {

    MemoryUnitSnapshot getMemoryUnit(String unitId);

    Result<MemorySliceSnapshot> getMemorySlice(String unitId, String sliceId);

    MemoryUnitSnapshot updateMemoryUnit(List<Message> chatMessages, String summary);

    Collection<MemoryUnitSnapshot> listMemoryUnits();

    void refreshMemorySession();

    String getMemorySessionId();

}
