package work.slhaf.agent.core.memory.pojo;

import lombok.Data;

import java.util.List;

@Data
public class MemoryResult {
    private List<MemorySliceResult> memorySliceResult;
    private List<MemorySlice> relatedMemorySliceResult;
}
