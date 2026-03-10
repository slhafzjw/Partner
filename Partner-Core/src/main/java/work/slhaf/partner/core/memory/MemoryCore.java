package work.slhaf.partner.core.memory;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.core.PartnerCore;
import work.slhaf.partner.core.memory.pojo.ActivatedMemorySlice;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;

import java.io.IOException;
import java.io.Serial;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private List<ActivatedMemorySlice> activatedSlices = new CopyOnWriteArrayList<>();
    private String currentMemoryId;

    public MemoryCore() throws IOException, ClassNotFoundException {
    }

    @CapabilityMethod
    public void clearActivatedSlices() {
        activatedSlices.clear();
    }

    @CapabilityMethod
    public void updateActivatedSlices(List<ActivatedMemorySlice> memorySlices) {
        activatedSlices = new CopyOnWriteArrayList<>(memorySlices);
    }

    @CapabilityMethod
    public boolean hasActivatedSlices() {
        return !activatedSlices.isEmpty();
    }

    @CapabilityMethod
    public int getActivatedSlicesSize() {
        return activatedSlices.size();
    }

    @CapabilityMethod
    public List<ActivatedMemorySlice> getActivatedSlices() {
        return new ArrayList<>(activatedSlices);
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
    public void refreshMemoryId() {
        currentMemoryId = UUID.randomUUID().toString();
    }

    @CapabilityMethod
    public String getCurrentMemoryId() {
        return currentMemoryId;
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
        int maxIndex = Math.max(memoryUnit.getConversationMessages().size() - 1, 0);
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
            if (slice.getEndIndex() == null || slice.getEndIndex() < slice.getStartIndex()) {
                slice.setEndIndex(maxIndex);
            }
            if (slice.getEndIndex() > maxIndex) {
                slice.setEndIndex(maxIndex);
            }
        }
        memoryUnit.getSlices().sort(Comparator.naturalOrder());
    }

    @Override
    protected String getCoreKey() {
        return "memory-core";
    }
}
