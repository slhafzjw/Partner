package work.slhaf.agent.core.memory.pojo;

import lombok.Data;

@Data
public class MemorySliceResult {
    private MemorySlice sliceBefore;
    private MemorySlice memorySlice;
    private MemorySlice sliceAfter;
}
