package work.slhaf.partner.core.memory.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.framework.agent.common.entity.PersistableObject;

import java.io.Serial;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemorySlice extends PersistableObject implements Comparable<MemorySlice> {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private final Integer startIndex;
    private final Integer endIndex;
    private final String summary;
    private final Long timestamp;

    public MemorySlice(Integer startIndex, Integer endIndex, String summary) {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.summary = summary;
    }

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
