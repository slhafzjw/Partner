package work.slhaf.agent.core.cognation.capability.ability;

import work.slhaf.agent.core.cognation.common.pojo.MemoryResult;
import work.slhaf.agent.core.cognation.submodule.memory.pojo.MemorySlice;

import java.io.IOException;
import java.time.LocalDate;

public interface MemoryCapability {
    MemoryResult selectMemory(String topicPathStr);
    MemoryResult selectMemory(LocalDate date) throws IOException, ClassNotFoundException;
    void insertSlice(MemorySlice memorySlice, String topicPath);
    void cleanSelectedSliceFilter();
    String getTopicTree();
}
