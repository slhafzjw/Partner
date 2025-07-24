package work.slhaf.partner.core.cognation.submodule.memory;

import work.slhaf.partner.api.factory.capability.annotation.Capability;
import work.slhaf.partner.api.factory.capability.annotation.ToCoordinated;
import work.slhaf.partner.core.cognation.common.pojo.MemoryResult;
import work.slhaf.partner.core.cognation.submodule.memory.pojo.MemorySlice;

import java.io.IOException;
import java.time.LocalDate;

@Capability(value = "memory")
public interface MemoryCapability {

    void cleanSelectedSliceFilter();
    String getTopicTree();

    @ToCoordinated
    MemoryResult selectMemory(String topicPathStr);

    @ToCoordinated
    MemoryResult selectMemory(LocalDate date) throws IOException, ClassNotFoundException;

    @ToCoordinated
    void insertSlice(MemorySlice memorySlice, String topicPath);
}
