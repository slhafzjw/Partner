package work.slhaf.partner.module.memory.runtime;

import work.slhaf.partner.core.memory.pojo.SliceRef;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

final class DateMemoryIndex {

    private final Map<LocalDate, CopyOnWriteArrayList<SliceRef>> dateIndex = new HashMap<>();

    void record(SliceRef sliceRef, LocalDate date) {
        dateIndex.computeIfAbsent(date, key -> new CopyOnWriteArrayList<>()).addIfAbsent(sliceRef);
    }

    List<SliceRef> find(LocalDate date) {
        List<SliceRef> refs = dateIndex.get(date);
        return refs == null ? null : new ArrayList<>(refs);
    }

    void reset() {
        dateIndex.clear();
    }

    void restore(LocalDate date, CopyOnWriteArrayList<SliceRef> refs) {
        dateIndex.put(date, refs);
    }

    Map<LocalDate, CopyOnWriteArrayList<SliceRef>> entries() {
        return dateIndex;
    }
}
