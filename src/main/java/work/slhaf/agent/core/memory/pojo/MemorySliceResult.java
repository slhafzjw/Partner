package work.slhaf.agent.core.memory.pojo;

import lombok.Data;
import work.slhaf.agent.core.memory.content.MemorySlice;

@Data
public class MemorySliceResult {
    private MemorySlice sliceBefore;
    private MemorySlice memorySlice;
    private MemorySlice sliceAfter;
}
