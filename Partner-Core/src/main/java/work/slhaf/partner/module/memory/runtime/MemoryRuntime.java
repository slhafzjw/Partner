package work.slhaf.partner.module.memory.runtime;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.core.memory.pojo.SliceRef;
import work.slhaf.partner.framework.agent.exception.ExceptionReporterHandler;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.state.State;
import work.slhaf.partner.framework.agent.state.StateSerializable;
import work.slhaf.partner.framework.agent.state.StateValue;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.memory.runtime.exception.MemoryLookupException;
import work.slhaf.partner.module.memory.selector.ActivatedMemorySlice;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class MemoryRuntime extends AbstractAgentModule.Standalone implements StateSerializable {

    @InjectCapability
    private MemoryCapability memoryCapability;
    @InjectCapability
    private CognitionCapability cognitionCapability;

    private final ReentrantLock runtimeLock = new ReentrantLock();
    private Map<String, CopyOnWriteArrayList<SliceRef>> topicSlices = new HashMap<>();
    private Map<LocalDate, CopyOnWriteArrayList<SliceRef>> dateIndex = new HashMap<>();

    @Init
    public void init() {
        register();
        checkAndSetMemoryId();
    }

    private void checkAndSetMemoryId() {
        if (cognitionCapability.getChatMessages().isEmpty()) {
            memoryCapability.refreshMemorySession();
        }
    }

    private void bindTopic(String topicPath, SliceRef sliceRef) {
        String normalizedPath = normalizeTopicPath(topicPath);
        runtimeLock.lock();
        try {
            CopyOnWriteArrayList<SliceRef> refs = topicSlices.computeIfAbsent(normalizedPath, key -> new CopyOnWriteArrayList<>());
            boolean exists = refs.stream().anyMatch(ref -> Objects.equals(ref.getUnitId(), sliceRef.getUnitId())
                    && Objects.equals(ref.getSliceId(), sliceRef.getSliceId()));
            if (!exists) {
                refs.add(sliceRef);
            }
        } finally {
            runtimeLock.unlock();
        }
    }

    public void recordMemory(MemoryUnit memoryUnit, String topicPath, List<String> relatedTopicPaths) {
        MemorySlice memorySlice = memoryUnit.getSlices().getLast();
        SliceRef sliceRef = new SliceRef(memoryUnit.getId(), memorySlice.getId());
        indexMemoryUnit(memoryUnit);
        if (topicPath != null && !topicPath.isBlank()) {
            bindTopic(topicPath, sliceRef);
        }
        for (String relatedTopicPath : relatedTopicPaths) {
            if (relatedTopicPath != null && !relatedTopicPath.isBlank()) {
                bindTopic(relatedTopicPath, sliceRef);
            }
        }
    }

    private void indexMemoryUnit(MemoryUnit memoryUnit) {
        runtimeLock.lock();
        try {
            for (CopyOnWriteArrayList<SliceRef> refs : dateIndex.values()) {
                refs.removeIf(ref -> memoryUnit.getId().equals(ref.getUnitId()));
            }
            for (MemorySlice slice : memoryUnit.getSlices()) {
                LocalDate date = Instant.ofEpochMilli(slice.getTimestamp())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
                dateIndex.computeIfAbsent(date, key -> new CopyOnWriteArrayList<>())
                        .addIfAbsent(new SliceRef(memoryUnit.getId(), slice.getId()));
            }
        } finally {
            runtimeLock.unlock();
        }
    }

    private List<SliceRef> findByTopicPath(String topicPath) {
        String normalizedPath = normalizeTopicPath(topicPath);
        List<SliceRef> refs = topicSlices.get(normalizedPath);
        if (refs == null) {
            ExceptionReporterHandler.INSTANCE.report(new MemoryLookupException(
                    "Unexisted topic path: " + normalizedPath,
                    normalizedPath,
                    "TOPIC"
            ));
            return List.of();
        }
        return new ArrayList<>(refs);
    }

    private List<SliceRef> findByDate(LocalDate date) {
        List<SliceRef> refs = dateIndex.get(date);
        if (refs == null) {
            ExceptionReporterHandler.INSTANCE.report(new MemoryLookupException(
                    "Unexisted date index: " + date,
                    date.toString(),
                    "DATE_INDEX"
            ));
            return List.of();
        }
        return new ArrayList<>(refs);
    }

    public List<ActivatedMemorySlice> queryActivatedMemoryByTopicPath(String topicPath) {
        return buildActivatedMemorySlices(findByTopicPath(topicPath));
    }

    public List<ActivatedMemorySlice> queryActivatedMemoryByDate(LocalDate date) {
        return buildActivatedMemorySlices(findByDate(date));
    }

    public String getTopicTree() {
        TopicTreeNode root = new TopicTreeNode();
        for (Map.Entry<String, CopyOnWriteArrayList<SliceRef>> entry : topicSlices.entrySet()) {
            String[] parts = entry.getKey().split("->");
            TopicTreeNode current = root;
            for (String part : parts) {
                current = current.children.computeIfAbsent(part, key -> new TopicTreeNode());
            }
            current.count += entry.getValue().size();
        }

        StringBuilder stringBuilder = new StringBuilder();
        List<Map.Entry<String, TopicTreeNode>> roots = new ArrayList<>(root.children.entrySet());
        for (Map.Entry<String, TopicTreeNode> entry : roots) {
            stringBuilder.append(entry.getKey()).append("[root]").append("\r\n");
            printSubTopicsTreeFormat(entry.getValue(), "", stringBuilder);
        }
        return stringBuilder.toString();
    }

    private List<ActivatedMemorySlice> buildActivatedMemorySlices(List<SliceRef> refs) {
        List<ActivatedMemorySlice> slices = new ArrayList<>();
        for (SliceRef ref : refs) {
            ActivatedMemorySlice slice = buildActivatedMemorySlice(ref);
            if (slice != null) {
                slices.add(slice);
            }
        }
        return slices;
    }

    private ActivatedMemorySlice buildActivatedMemorySlice(SliceRef ref) {
        MemoryUnit memoryUnit = memoryCapability.getMemoryUnit(ref.getUnitId());
        Result<MemorySlice> memorySliceResult = memoryCapability.getMemorySlice(ref.getUnitId(), ref.getSliceId());
        if (memorySliceResult.exceptionOrNull() != null) {
            return null;
        }
        MemorySlice memorySlice = memorySliceResult.getOrThrow();
        List<Message> messages = sliceMessages(memoryUnit, memorySlice);
        LocalDate date = Instant.ofEpochMilli(memorySlice.getTimestamp())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        return ActivatedMemorySlice.builder()
                .unitId(ref.getUnitId())
                .sliceId(ref.getSliceId())
                .summary(memorySlice.getSummary())
                .timestamp(memorySlice.getTimestamp())
                .date(date)
                .messages(messages)
                .build();
    }

    private List<Message> sliceMessages(MemoryUnit memoryUnit, MemorySlice memorySlice) {
        List<Message> conversationMessages = memoryUnit.getConversationMessages();
        if (conversationMessages.isEmpty()) {
            return List.of();
        }
        int size = conversationMessages.size();
        int start = Math.clamp(memorySlice.getStartIndex(), 0, size);
        int end = Math.clamp(memorySlice.getEndIndex(), start, size);
        if (start >= end) {
            return List.of();
        }
        return new ArrayList<>(conversationMessages.subList(start, end));
    }

    private void printSubTopicsTreeFormat(TopicTreeNode node, String prefix, StringBuilder stringBuilder) {
        List<Map.Entry<String, TopicTreeNode>> entries = new ArrayList<>(node.children.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            boolean last = i == entries.size() - 1;
            Map.Entry<String, TopicTreeNode> entry = entries.get(i);
            stringBuilder.append(prefix)
                    .append(last ? "└── " : "├── ")
                    .append(entry.getKey())
                    .append("[")
                    .append(entry.getValue().count)
                    .append("]")
                    .append("\r\n");
            printSubTopicsTreeFormat(entry.getValue(), prefix + (last ? "    " : "│   "), stringBuilder);
        }
    }

    private String normalizeTopicPath(String topicPath) {
        return topicPath == null ? "" : topicPath.trim();
    }

    @Override
    @NotNull
    public Path statePath() {
        return Path.of("module", "memory", "topic_based_memory.json");
    }

    @Override
    public void load(@NotNull JSONObject state) {
        runtimeLock.lock();
        try {
            topicSlices = new HashMap<>();
            dateIndex = new HashMap<>();

            JSONArray topicSlicesArray = state.getJSONArray("topic_slices");
            if (topicSlicesArray != null) {
                for (int i = 0; i < topicSlicesArray.size(); i++) {
                    JSONObject topicObject = topicSlicesArray.getJSONObject(i);
                    if (topicObject == null) {
                        continue;
                    }
                    String topicPath = topicObject.getString("topic_path");
                    if (topicPath == null) {
                        continue;
                    }
                    topicSlices.put(normalizeTopicPath(topicPath), decodeSliceRefs(topicObject.getJSONArray("refs")));
                }
            }

            JSONArray dateIndexArray = state.getJSONArray("date_index");
            if (dateIndexArray != null) {
                for (int i = 0; i < dateIndexArray.size(); i++) {
                    JSONObject dateObject = dateIndexArray.getJSONObject(i);
                    if (dateObject == null) {
                        continue;
                    }
                    String date = dateObject.getString("date");
                    if (date == null) {
                        continue;
                    }
                    try {
                        dateIndex.put(LocalDate.parse(date), decodeSliceRefs(dateObject.getJSONArray("refs")));
                    } catch (Exception e) {
                        log.warn("skip invalid date index: {}", date, e);
                    }
                }
            }
        } finally {
            runtimeLock.unlock();
        }
    }

    @Override
    public @NotNull State convert() {
        runtimeLock.lock();
        try {
            State state = new State();

            List<StateValue.Obj> topicSliceStates = topicSlices.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> StateValue.obj(Map.of(
                            "topic_path", StateValue.str(entry.getKey()),
                            "refs", StateValue.arr(encodeSliceRefs(entry.getValue()))
                    )))
                    .toList();
            state.append("topic_slices", StateValue.arr(topicSliceStates));

            List<StateValue.Obj> dateIndexStates = dateIndex.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> StateValue.obj(Map.of(
                            "date", StateValue.str(entry.getKey().toString()),
                            "refs", StateValue.arr(encodeSliceRefs(entry.getValue()))
                    )))
                    .toList();
            state.append("date_index", StateValue.arr(dateIndexStates));

            return state;
        } finally {
            runtimeLock.unlock();
        }
    }

    private List<StateValue> encodeSliceRefs(List<SliceRef> refs) {
        return refs.stream()
                .map(ref -> (StateValue) StateValue.obj(Map.of(
                        "unit_id", StateValue.str(ref.getUnitId()),
                        "slice_id", StateValue.str(ref.getSliceId())
                )))
                .toList();
    }

    private CopyOnWriteArrayList<SliceRef> decodeSliceRefs(JSONArray refsArray) {
        CopyOnWriteArrayList<SliceRef> refs = new CopyOnWriteArrayList<>();
        if (refsArray == null) {
            return refs;
        }
        for (int i = 0; i < refsArray.size(); i++) {
            JSONObject refObject = refsArray.getJSONObject(i);
            if (refObject == null) {
                continue;
            }
            String unitId = refObject.getString("unit_id");
            String sliceId = refObject.getString("slice_id");
            if (unitId == null || sliceId == null) {
                continue;
            }
            refs.addIfAbsent(new SliceRef(unitId, sliceId));
        }
        return refs;
    }

    public String fixTopicPath(String topicPath) {
        String[] parts = topicPath.split("->");
        List<String> cleanedParts = new ArrayList<>();

        for (String part : parts) {
            // 修正正则表达式，正确移除 [xxx] 部分
            String cleaned = part.replaceAll("\\[[^\\]]*\\]", "").trim();
            if (!cleaned.isEmpty()) { // 忽略空字符串
                cleanedParts.add(cleaned);
            }
        }

        return String.join("->", cleanedParts);
    }

    private static final class TopicTreeNode {
        private final Map<String, TopicTreeNode> children = new LinkedHashMap<>();
        private int count;
    }
}
