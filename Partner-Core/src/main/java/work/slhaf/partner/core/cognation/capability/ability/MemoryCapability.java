package work.slhaf.partner.core.cognation.capability.ability;

import work.slhaf.partner.core.cognation.common.pojo.MemoryResult;
import work.slhaf.partner.core.cognation.submodule.memory.pojo.MemorySlice;

import java.io.IOException;
import java.time.LocalDate;

public interface MemoryCapability {
    MemoryResult selectMemory(String topicPathStr);
    MemoryResult selectMemory(LocalDate date) throws IOException, ClassNotFoundException;
    void insertSlice(MemorySlice memorySlice, String topicPath);
    void cleanSelectedSliceFilter();
    String getTopicTree();
}
