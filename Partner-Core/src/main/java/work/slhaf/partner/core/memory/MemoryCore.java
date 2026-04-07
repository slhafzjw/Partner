package work.slhaf.partner.core.memory;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.framework.agent.state.State;
import work.slhaf.partner.framework.agent.state.StateSerializable;
import work.slhaf.partner.framework.agent.state.StateValue;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@CapabilityCore(value = "memory")
@Slf4j
public class MemoryCore implements StateSerializable {

    private final Lock memoryLock = new ReentrantLock();
    private final ConcurrentHashMap<String, MemoryUnit> memoryUnits = new ConcurrentHashMap<>();

    // 默认值一般只存在于智能体初次启动时
    private String memorySessionId = UUID.randomUUID().toString();

    public MemoryCore() {
        register();
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
        MemoryUnit unit = memoryUnits.computeIfAbsent(unitId, id -> {
            MemoryUnit newUnit = new MemoryUnit(id);
            newUnit.register();
            return newUnit;
        });
        unit.load();
        return unit;
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
    }

    @CapabilityMethod
    public String getMemorySessionId() {
        return memorySessionId;
    }

    private void normalizeMemoryUnit(MemoryUnit memoryUnit) {
        if (memoryUnit.getTimestamp() == null || memoryUnit.getTimestamp() <= 0) {
            memoryUnit.updateTimestamp();
        }
        int maxEndExclusive = memoryUnit.getConversationMessages().size();
        List<MemorySlice> normalizedSlices = new ArrayList<>(memoryUnit.getSlices().size());
        for (MemorySlice slice : memoryUnit.getSlices()) {
            if (slice == null) {
                continue;
            }
            String sliceId = slice.getId();
            if (sliceId == null || sliceId.isBlank()) {
                sliceId = UUID.randomUUID().toString();
            }
            long sliceTimestamp = slice.getTimestamp() == null || slice.getTimestamp() <= 0
                    ? memoryUnit.getTimestamp()
                    : slice.getTimestamp();
            int startIndex = slice.getStartIndex() == null || slice.getStartIndex() < 0
                    ? 0
                    : Math.min(slice.getStartIndex(), maxEndExclusive);
            int endIndex = slice.getEndIndex() == null || slice.getEndIndex() < startIndex
                    ? maxEndExclusive
                    : Math.min(slice.getEndIndex(), maxEndExclusive);
            normalizedSlices.add(MemorySlice.restore(
                    sliceId,
                    startIndex,
                    endIndex,
                    slice.getSummary(),
                    sliceTimestamp
            ));
        }
        memoryUnit.getSlices().clear();
        memoryUnit.getSlices().addAll(normalizedSlices);
        memoryUnit.getSlices().sort(Comparator.naturalOrder());
    }

    @Override
    public @NotNull Path statePath() {
        return Path.of("core", "memory.json");
    }

    @Override
    public void load(@NotNull JSONObject state) {
        String memorySessionId = state.getString("memory_session_id");
        if (memorySessionId == null) {
            throw new IllegalStateException("Memory session id is missing");
        }
        JSONArray array = state.getJSONArray("memory_unit_uuid_set");
        if (array == null) {
            throw new IllegalStateException("Memory unit uuid set is missing");
        }
        for (int i = 0; i < array.size(); i++) {
            String unitUuid = array.getString(i);
            if (unitUuid == null) {
                throw new IllegalStateException("memory_unit_uuid_set is not a uuid array, index: " + i);
            }
            MemoryUnit memoryUnit = new MemoryUnit(unitUuid);
            memoryUnits.put(unitUuid, memoryUnit);
        }
    }

    @Override
    public @NotNull State convert() {
        State state = new State();
        state.append("memory_session_id", StateValue.str(memorySessionId));

        List<StateValue.Str> unitOverview = memoryUnits.keySet().stream()
                .map(StateValue::str)
                .toList();
        state.append("memory_unit_uuid_set", StateValue.arr(unitOverview));
        return state;
    }
}
