package work.slhaf.agent.core.memory.pojo;

import lombok.Data;
import work.slhaf.agent.core.memory.content.MemorySlice;

import java.util.List;

@Data
public class MemoryResult {
    private List<MemorySliceResult> memorySliceResult;
    private List<MemorySlice> relatedMemorySliceResult;
}
