package work.slhaf.partner.core.memory.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.framework.agent.common.entity.PersistableObject;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemorySlice extends PersistableObject implements Comparable<MemorySlice> {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private Integer startIndex;
    private Integer endIndex;
    private String summary;
    private Long timestamp;

    @Override
    public int compareTo(MemorySlice memorySlice) {
        if (memorySlice.getTimestamp() > this.getTimestamp()) {
            return -1;
        } else if (memorySlice.getTimestamp() < this.timestamp) {
            return 1;
        }
        return 0;
    }
}
