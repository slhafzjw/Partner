package work.slhaf.memory.node;

import lombok.Data;
import work.slhaf.memory.content.MemorySlice;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MemoryNode implements Serializable {
    private LocalDateTime localDateTime;
    private List<MemorySlice> memorySliceList;
}
