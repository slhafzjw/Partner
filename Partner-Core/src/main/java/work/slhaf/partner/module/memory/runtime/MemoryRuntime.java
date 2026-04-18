package work.slhaf.partner.module.memory.runtime;

import com.alibaba.fastjson2.JSONObject;
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
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.memory.pojo.ActivationProfile;
import work.slhaf.partner.module.memory.runtime.exception.MemoryLookupException;
import work.slhaf.partner.module.memory.selector.ActivatedMemorySlice;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class MemoryRuntime extends AbstractAgentModule.Standalone implements StateSerializable {

    @InjectCapability
    private MemoryCapability memoryCapability;
    @InjectCapability
    private CognitionCapability cognitionCapability;

    private final ReentrantLock runtimeLock = new ReentrantLock();
    private final TopicMemoryIndex topicIndex = new TopicMemoryIndex();
    private final DateMemoryIndex dateIndex = new DateMemoryIndex();
    private final TopicRecallCollector topicRecallCollector = new TopicRecallCollector(new TopicRecallScorer());
    private final MemoryRuntimeStateCodec stateCodec = new MemoryRuntimeStateCodec();

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

    public void recordMemory(MemoryUnit memoryUnit,
                             String topicPath,
                             List<String> relatedTopicPaths,
                             ActivationProfile activationProfile) {
        MemorySlice memorySlice = memoryUnit.getSlices().getLast();
        SliceRef sliceRef = new SliceRef(memoryUnit.getId(), memorySlice.getId());
        LocalDate date = toLocalDate(memorySlice.getTimestamp());
        runtimeLock.lock();
        try {
            List<String> normalizedRelatedTopicPaths = topicIndex.normalizeTopicPaths(relatedTopicPaths);
            dateIndex.record(sliceRef, date);
            if (topicPath != null && !topicPath.isBlank()) {
                topicIndex.recordBinding(
                        topicPath,
                        sliceRef,
                        memorySlice.getTimestamp(),
                        normalizedRelatedTopicPaths,
                        activationProfile
                );
            }
            topicIndex.ensureTopicPaths(normalizedRelatedTopicPaths);
        } finally {
            runtimeLock.unlock();
        }
    }

    public List<ActivatedMemorySlice> queryActivatedMemoryByTopicPath(String topicPath) {
        return buildActivatedMemorySlices(findByTopicPath(topicPath));
    }

    public List<ActivatedMemorySlice> queryActivatedMemoryByDate(LocalDate date) {
        return buildActivatedMemorySlices(findByDate(date));
    }

    public String getTopicTree() {
        runtimeLock.lock();
        try {
            return topicIndex.getTopicTree();
        } finally {
            runtimeLock.unlock();
        }
    }

    public String fixTopicPath(String topicPath) {
        String[] parts = topicPath.split("->");
        List<String> cleanedParts = new ArrayList<>();
        for (String part : parts) {
            String cleaned = part.replaceAll("\\[[^]]*]", "").trim();
            if (!cleaned.isEmpty()) {
                cleanedParts.add(cleaned);
            }
        }
        return String.join("->", cleanedParts);
    }

    private List<SliceRef> findByTopicPath(String topicPath) {
        runtimeLock.lock();
        try {
            TopicMemoryIndex.TopicTreeNode topicNode = topicIndex.findTopicNode(topicPath);
            if (topicNode == null) {
                String normalizedPath = topicIndex.normalizeTopicPath(topicPath);
                ExceptionReporterHandler.INSTANCE.report(new MemoryLookupException(
                        "Unexisted topic path: " + normalizedPath,
                        normalizedPath,
                        "TOPIC"
                ));
                return List.of();
            }
            return topicRecallCollector.collect(topicIndex, topicNode);
        } finally {
            runtimeLock.unlock();
        }
    }

    private List<SliceRef> findByDate(LocalDate date) {
        runtimeLock.lock();
        try {
            List<SliceRef> refs = dateIndex.find(date);
            if (refs == null) {
                ExceptionReporterHandler.INSTANCE.report(new MemoryLookupException(
                        "Unexisted date index: " + date,
                        date.toString(),
                        "DATE_INDEX"
                ));
                return List.of();
            }
            return refs;
        } finally {
            runtimeLock.unlock();
        }
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
        if (memoryUnit == null || memorySliceResult.exceptionOrNull() != null) {
            return null;
        }
        MemorySlice memorySlice = memorySliceResult.getOrThrow();
        List<Message> messages = sliceMessages(memoryUnit, memorySlice);
        LocalDate date = toLocalDate(memorySlice.getTimestamp());
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

    private LocalDate toLocalDate(Long timestamp) {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    @Override
    public @NotNull Path statePath() {
        return Path.of("module", "memory", "topic_based_memory.json");
    }

    @Override
    public void load(@NotNull JSONObject state) {
        runtimeLock.lock();
        try {
            stateCodec.load(state, topicIndex, dateIndex);
        } finally {
            runtimeLock.unlock();
        }
    }

    @Override
    public @NotNull State convert() {
        runtimeLock.lock();
        try {
            return stateCodec.convert(topicIndex, dateIndex);
        } finally {
            runtimeLock.unlock();
        }
    }
}
