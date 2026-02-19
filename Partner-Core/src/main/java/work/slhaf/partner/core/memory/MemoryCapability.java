package work.slhaf.partner.core.memory;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.core.memory.pojo.EvaluatedSlice;
import work.slhaf.partner.core.memory.pojo.MemoryResult;
import work.slhaf.partner.core.memory.pojo.MemorySlice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Capability(value = "memory")
public interface MemoryCapability {

    void cleanSelectedSliceFilter();

    String getTopicTree();

    HashMap<LocalDateTime, String> getDialogMap();

    ConcurrentHashMap<LocalDateTime, String> getUserDialogMap(String userId);

    void updateDialogMap(LocalDateTime dateTime, String newDialogCache);

    String getDialogMapStr();

    String getUserDialogMapStr(String userId);

    void updateActivatedSlices(String userId, List<EvaluatedSlice> memorySlices);

    String getActivatedSlicesStr(String userId);

    HashMap<String, List<EvaluatedSlice>> getActivatedSlices();

    void clearActivatedSlices(String userId);

    boolean hasActivatedSlices(String userId);

    int getActivatedSlicesSize(String userId);

    List<EvaluatedSlice> getActivatedSlices(String userId);

    MemoryResult selectMemory(String topicPathStr);

    MemoryResult selectMemory(LocalDate date);

    void insertSlice(MemorySlice memorySlice, String topicPath);

}
