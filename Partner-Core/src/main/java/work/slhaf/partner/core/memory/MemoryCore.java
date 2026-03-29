package work.slhaf.partner.core.memory;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.core.PartnerCore;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;

import java.io.IOException;
import java.io.Serial;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@CapabilityCore(value = "memory")
@Getter
@Setter
@Slf4j
public class MemoryCore extends PartnerCore<MemoryCore> {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Lock memoryLock = new ReentrantLock();
    private ConcurrentHashMap<String, MemoryUnit> memoryUnits = new ConcurrentHashMap<>();

    // 默认值一般只存在于智能体初次启动时
    private String memorySessionId = UUID.randomUUID().toString();
    private Instant memorySessionStartTime = Instant.now();

    public MemoryCore() throws IOException, ClassNotFoundException {
    }

    @CapabilityMethod
    public void saveMemoryUnit(MemoryUnit memoryUnit) {
        memoryLock.lock();
        try {
            normalizeMemoryUnit(memoryUnit);
            memoryUnits.put(memoryUnit.getId(), memoryUnit);
        } finally {
            memoryLock.unlock();
        }
    }

    @CapabilityMethod
    public MemoryUnit getMemoryUnit(String unitId) {
        return memoryUnits.get(unitId);
    }

    @CapabilityMethod
    public MemorySlice getMemorySlice(String unitId, String sliceId) {
        MemoryUnit memoryUnit = memoryUnits.get(unitId);
        if (memoryUnit == null || memoryUnit.getSlices() == null) {
            return null;
        }
        for (MemorySlice slice : memoryUnit.getSlices()) {
            if (sliceId.equals(slice.getId())) {
                return slice;
            }
        }
        return null;
    }

    @CapabilityMethod
    public Collection<MemoryUnit> listMemoryUnits() {
        return new ArrayList<>(memoryUnits.values());
    }

    @CapabilityMethod
    public void refreshMemorySession() {
        memorySessionId = UUID.randomUUID().toString();
        memorySessionStartTime = Instant.now();
    }

    @CapabilityMethod
    public String getMemorySessionId() {
        return memorySessionId;
    }

    private void normalizeMemoryUnit(MemoryUnit memoryUnit) {
        if (memoryUnit.getId() == null || memoryUnit.getId().isBlank()) {
            memoryUnit.setId(UUID.randomUUID().toString());
        }
        if (memoryUnit.getTimestamp() == null || memoryUnit.getTimestamp() <= 0) {
            memoryUnit.setTimestamp(System.currentTimeMillis());
        }
        if (memoryUnit.getConversationMessages() == null) {
            memoryUnit.setConversationMessages(new ArrayList<>());
        }
        if (memoryUnit.getSlices() == null) {
            memoryUnit.setSlices(new ArrayList<>());
        }
        int maxEndExclusive = Math.max(memoryUnit.getConversationMessages().size(), 0);
        for (MemorySlice slice : memoryUnit.getSlices()) {
            if (slice.getId() == null || slice.getId().isBlank()) {
                slice.setId(UUID.randomUUID().toString());
            }
            if (slice.getTimestamp() == null || slice.getTimestamp() <= 0) {
                slice.setTimestamp(memoryUnit.getTimestamp());
            }
            if (slice.getStartIndex() == null || slice.getStartIndex() < 0) {
                slice.setStartIndex(0);
            }
            if (slice.getStartIndex() > maxEndExclusive) {
                slice.setStartIndex(maxEndExclusive);
            }
            if (slice.getEndIndex() == null || slice.getEndIndex() < slice.getStartIndex()) {
                slice.setEndIndex(maxEndExclusive);
            }
            if (slice.getEndIndex() > maxEndExclusive) {
                slice.setEndIndex(maxEndExclusive);
            }
        }
        memoryUnit.getSlices().sort(Comparator.naturalOrder());
    }

    @Override
    protected String getCoreKey() {
        return "memory-core";
    }
}
