package work.slhaf.agent.core.cognation.common.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.serialize.PersistableObject;
import work.slhaf.agent.core.cognation.submodule.memory.pojo.MemorySlice;

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

    public boolean isEmpty(){
        boolean a = memorySliceResult == null || memorySliceResult.isEmpty();
        boolean b = relatedMemorySliceResult == null || relatedMemorySliceResult.isEmpty();
        return a && b;
    }
}
