package work.slhaf.partner.module.memory.runtime;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.annotation.Init;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigLoader;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.api.common.entity.PersistableObject;
import work.slhaf.partner.common.config.PartnerAgentConfigLoader;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.exception.UnExistedDateIndexException;
import work.slhaf.partner.core.memory.exception.UnExistedTopicException;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.core.memory.pojo.SliceRef;
import work.slhaf.partner.module.memory.selector.ActivatedMemorySlice;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

import static work.slhaf.partner.common.Constant.Path.MEMORY_DATA;

@EqualsAndHashCode(callSuper = true)
@Slf4j
public class MemoryRuntime extends AbstractAgentModule.Standalone {

    private static final String RUNTIME_KEY = "memory-runtime";

    @InjectCapability
    private MemoryCapability memoryCapability;
    @InjectCapability
    private CognitionCapability cognitionCapability;

    private final ReentrantLock runtimeLock = new ReentrantLock();
    private Map<String, CopyOnWriteArrayList<SliceRef>> topicSlices = new HashMap<>();
    private Map<LocalDate, CopyOnWriteArrayList<SliceRef>> dateIndex = new HashMap<>();

    @Init
    public void init() {
        loadState();
        checkAndSetMemoryId();
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveStateSafely));
    }

    private void checkAndSetMemoryId() {
        String currentMemoryId = memoryCapability.getMemorySessionId();
        if (currentMemoryId == null || cognitionCapability.getChatMessages().isEmpty()) {
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
            saveState();
        } finally {
            runtimeLock.unlock();
        }
    }

    public void recordMemory(MemoryUnit memoryUnit, String topicPath, List<String> relatedTopicPaths) {
        memoryCapability.saveMemoryUnit(memoryUnit);
        MemorySlice memorySlice = memoryUnit.getSlices().getLast();
        SliceRef sliceRef = new SliceRef(memoryUnit.getId(), memorySlice.getId());
        indexMemoryUnit(memoryUnit);
        bindTopic(topicPath, sliceRef);
        if (relatedTopicPaths != null) {
            for (String relatedTopicPath : relatedTopicPaths) {
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
            if (memoryUnit.getSlices() != null) {
                for (MemorySlice slice : memoryUnit.getSlices()) {
                    LocalDate date = Instant.ofEpochMilli(slice.getTimestamp())
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate();
                    dateIndex.computeIfAbsent(date, key -> new CopyOnWriteArrayList<>())
                            .addIfAbsent(new SliceRef(memoryUnit.getId(), slice.getId()));
                }
            }
            saveState();
        } finally {
            runtimeLock.unlock();
        }
    }

    private List<SliceRef> findByTopicPath(String topicPath) {
        String normalizedPath = normalizeTopicPath(topicPath);
        List<SliceRef> refs = topicSlices.get(normalizedPath);
        if (refs == null || refs.isEmpty()) {
            throw new UnExistedTopicException("不存在的主题: " + normalizedPath);
        }
        return new ArrayList<>(refs);
    }

    private List<SliceRef> findByDate(LocalDate date) {
        List<SliceRef> refs = dateIndex.get(date);
        if (refs == null || refs.isEmpty()) {
            throw new UnExistedDateIndexException("不存在的日期索引: " + date);
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
        MemorySlice memorySlice = memoryCapability.getMemorySlice(ref.getUnitId(), ref.getSliceId());
        if (memoryUnit == null || memorySlice == null) {
            return null;
        }
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
        if (conversationMessages == null || conversationMessages.isEmpty()) {
            return List.of();
        }
        int size = conversationMessages.size();
        int start = Math.max(0, Math.min(memorySlice.getStartIndex(), size));
        int end = Math.max(start, Math.min(memorySlice.getEndIndex(), size));
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

    private void loadState() {
        Path filePath = getFilePath();
        if (!Files.exists(filePath)) {
            return;
        }
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath.toFile()))) {
            RuntimeState state = (RuntimeState) ois.readObject();
            topicSlices = state.topicSlices;
            dateIndex = state.dateIndex;
        } catch (Exception e) {
            log.error("[MemoryRuntime] 加载运行态失败", e);
            topicSlices = new HashMap<>();
            dateIndex = new HashMap<>();
        }
    }

    private void saveStateSafely() {
        runtimeLock.lock();
        try {
            saveState();
        } finally {
            runtimeLock.unlock();
        }
    }

    private void saveState() {
        Path filePath = getFilePath();
        Path tempPath = getTempFilePath();
        try {
            Files.createDirectories(Paths.get(MEMORY_DATA));
            FileUtils.createParentDirectories(filePath.toFile());
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tempPath.toFile()))) {
                RuntimeState state = new RuntimeState();
                state.topicSlices = new HashMap<>(topicSlices);
                state.dateIndex = new HashMap<>(dateIndex);
                oos.writeObject(state);
            }
            Files.move(tempPath, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("[MemoryRuntime] 保存运行态失败", e);
        }
    }

    private Path getFilePath() {
        String id = ((PartnerAgentConfigLoader) AgentConfigLoader.INSTANCE).getConfig().getAgentId();
        return Paths.get(MEMORY_DATA, id + "-" + RUNTIME_KEY + ".memory");
    }

    private Path getTempFilePath() {
        String id = ((PartnerAgentConfigLoader) AgentConfigLoader.INSTANCE).getConfig().getAgentId();
        return Paths.get(MEMORY_DATA, id + "-" + RUNTIME_KEY + "-temp.memory");
    }

    private static final class TopicTreeNode {
        private final Map<String, TopicTreeNode> children = new LinkedHashMap<>();
        private int count;
    }

    private static final class RuntimeState extends PersistableObject {
        @Serial
        private static final long serialVersionUID = 1L;

        private Map<String, CopyOnWriteArrayList<SliceRef>> topicSlices = new HashMap<>();
        private Map<LocalDate, CopyOnWriteArrayList<SliceRef>> dateIndex = new HashMap<>();
    }
}
