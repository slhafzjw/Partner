package work.slhaf.demo.ability;


import work.slhaf.partner.api.factory.capability.annotation.Capability;
import work.slhaf.partner.api.factory.capability.annotation.ToCoordinated;

import java.util.List;

@Capability(value = "memory")
public interface MemoryCapability {
    void cleanSelectedSliceFilter();
    String getTopicTree();
    List<String> listMemory(String userId);
    @ToCoordinated
    List<String> selectMemory(String path);
}
