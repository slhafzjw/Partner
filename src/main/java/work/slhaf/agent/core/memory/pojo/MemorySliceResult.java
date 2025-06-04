package work.slhaf.agent.core.memory.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.serialize.PersistableObject;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemorySliceResult extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private MemorySlice sliceBefore;
    private MemorySlice memorySlice;
    private MemorySlice sliceAfter;
}
