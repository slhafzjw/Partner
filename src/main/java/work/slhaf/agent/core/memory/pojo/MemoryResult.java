package work.slhaf.agent.core.memory.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.pojo.PersistableObject;

import java.io.Serial;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemoryResult extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private CopyOnWriteArrayList<MemorySliceResult> memorySliceResult;
    private List<MemorySlice> relatedMemorySliceResult;
}
