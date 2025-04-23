package work.slhaf.agent.core.memory.pojo;

import lombok.Data;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
public class MemoryResult {
    private CopyOnWriteArrayList<MemorySliceResult> memorySliceResult;
    private List<MemorySlice> relatedMemorySliceResult;
}
