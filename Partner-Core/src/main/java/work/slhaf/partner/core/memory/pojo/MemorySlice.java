package work.slhaf.partner.core.memory.pojo;

import lombok.Getter;

import java.util.UUID;

@Getter
public class MemorySlice implements Comparable<MemorySlice> {

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

    private MemorySlice(String id, Integer startIndex, Integer endIndex, String summary, Long timestamp) {
        this.id = id;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.summary = summary;
        this.timestamp = timestamp;
    }

    public static MemorySlice restore(String id, Integer startIndex, Integer endIndex, String summary, Long timestamp) {
        return new MemorySlice(id, startIndex, endIndex, summary, timestamp);
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
