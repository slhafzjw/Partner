package work.slhaf.demo.capability.ability;

import work.slhaf.demo.capability.interfaces.Capability;
import work.slhaf.demo.capability.interfaces.ToCoordinated;

import java.util.List;

@Capability(value = "memory")
public interface MemoryCapability {
    void cleanSelectedSliceFilter();
    String getTopicTree();
    List<String> listMemory(String userId);
    @ToCoordinated
    List<String> selectMemory(String path);
}
