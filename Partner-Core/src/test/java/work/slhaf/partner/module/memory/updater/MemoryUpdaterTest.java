package work.slhaf.partner.module.memory.updater;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.memory.runtime.MemoryRuntime;
import work.slhaf.partner.module.memory.runtime.exception.MemoryLookupException;
import work.slhaf.partner.module.memory.updater.summarizer.MultiSummarizer;
import work.slhaf.partner.module.memory.updater.summarizer.SingleSummarizer;
import work.slhaf.partner.module.memory.updater.summarizer.entity.SummarizeResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryUpdaterTest {

    @BeforeAll
    static void beforeAll(@TempDir Path tempDir) {
        System.setProperty("user.home", tempDir.toAbsolutePath().toString());
    }

    private static Object invokeUpdateMemory(MemoryUpdater updater, List<Message> chatMessages) throws Exception {
        Method method = MemoryUpdater.class.getDeclaredMethod("updateMemory", List.class);
        method.setAccessible(true);
        return method.invoke(updater, chatMessages);
    }

    @SuppressWarnings("unchecked")
    private static List<Message> invokeResolveChatIncrement(MemoryUpdater updater,
                                                            List<Message> chatMessages) throws Exception {
        Method method = MemoryUpdater.class.getDeclaredMethod("resolveChatIncrement", List.class);
        method.setAccessible(true);
        return (List<Message>) method.invoke(updater, chatMessages);
    }

    private static String recordField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static SummarizeResult summarizeResult(String summary, String topicPath, List<String> relatedTopicPath) {
        SummarizeResult result = new SummarizeResult();
        result.setSummary(summary);
        result.setTopicPath(topicPath);
        result.setRelatedTopicPath(relatedTopicPath);
        return result;
    }

    private static Message message(Message.Character role, String content) {
        return new Message(role, content);
    }

    @Test
    void shouldDelegateMemoryUpdateToCapabilityAndRuntime() throws Exception {
        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-1");
        MemoryUpdater updater = new MemoryUpdater();
        MemoryRuntime memoryRuntime = Mockito.mock(MemoryRuntime.class);
        MultiSummarizer multiSummarizer = Mockito.mock(MultiSummarizer.class);
        SingleSummarizer singleSummarizer = Mockito.mock(SingleSummarizer.class);
        setField(updater, "memoryCapability", memoryCapability);
        setField(updater, "memoryRuntime", memoryRuntime);
        setField(updater, "multiSummarizer", multiSummarizer);
        setField(updater, "singleSummarizer", singleSummarizer);

        when(memoryRuntime.getTopicTree()).thenReturn("topic-tree");
        when(multiSummarizer.execute(Mockito.any())).thenReturn(Result.success(
                summarizeResult("new-summary", "topic/main", List.of("topic/related"))
        ));

        MemoryUnit existingUnit = new MemoryUnit("session-1");
        existingUnit.getConversationMessages().addAll(List.of(
                message(Message.Character.USER, "old-user"),
                message(Message.Character.ASSISTANT, "old-assistant")
        ));
        existingUnit.getSlices().add(MemorySlice.restore("slice-1", 0, 2, "old-summary", 1L));
        memoryCapability.putUnit(existingUnit);

        Object rollingRecord = invokeUpdateMemory(updater, List.of(
                message(Message.Character.USER, "new-user"),
                message(Message.Character.ASSISTANT, "new-assistant")
        ));

        MemoryUnit merged = memoryCapability.getMemoryUnit("session-1");
        assertEquals(List.of("old-user", "old-assistant", "new-user", "new-assistant"),
                merged.getConversationMessages().stream().map(Message::getContent).toList());
        assertEquals(2, merged.getSlices().size());

        MemorySlice appendedSlice = merged.getSlices().getLast();
        assertNotNull(appendedSlice.getId());
        assertEquals(2, appendedSlice.getStartIndex());
        assertEquals(4, appendedSlice.getEndIndex());
        assertEquals("new-summary", appendedSlice.getSummary());

        assertEquals(List.of("new-user", "new-assistant"),
                memoryCapability.lastChatMessages().stream().map(Message::getContent).toList());
        assertEquals("new-summary", memoryCapability.lastSummary());
        verify(memoryRuntime).recordMemory(eq(merged), eq("topic/main"), eq(List.of("topic/related")));
        assertEquals("session-1", recordField(rollingRecord, "unitId"));
        assertEquals(appendedSlice.getId(), recordField(rollingRecord, "sliceId"));
        assertEquals("new-summary", recordField(rollingRecord, "summary"));
    }

    @Test
    void shouldCreateFirstSliceForFreshSessionThroughCapability() throws Exception {
        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-2");
        MemoryUpdater updater = new MemoryUpdater();
        MemoryRuntime memoryRuntime = Mockito.mock(MemoryRuntime.class);
        MultiSummarizer multiSummarizer = Mockito.mock(MultiSummarizer.class);
        SingleSummarizer singleSummarizer = Mockito.mock(SingleSummarizer.class);
        setField(updater, "memoryCapability", memoryCapability);
        setField(updater, "memoryRuntime", memoryRuntime);
        setField(updater, "multiSummarizer", multiSummarizer);
        setField(updater, "singleSummarizer", singleSummarizer);

        when(memoryRuntime.getTopicTree()).thenReturn("topic-tree");
        when(multiSummarizer.execute(Mockito.any())).thenReturn(Result.success(
                summarizeResult("fresh-summary", "topic/root", List.of())
        ));

        Object rollingRecord = invokeUpdateMemory(updater, List.of(
                message(Message.Character.USER, "first"),
                message(Message.Character.ASSISTANT, "second")
        ));

        MemoryUnit created = memoryCapability.getMemoryUnit("session-2");
        assertNotNull(created);
        assertEquals("session-2", created.getId());
        assertEquals(List.of("first", "second"),
                created.getConversationMessages().stream().map(Message::getContent).toList());
        assertEquals(1, created.getSlices().size());
        assertEquals(0, created.getSlices().getFirst().getStartIndex());
        assertEquals(2, created.getSlices().getFirst().getEndIndex());
        assertEquals("fresh-summary", created.getSlices().getFirst().getSummary());
        verify(memoryRuntime).recordMemory(eq(created), eq("topic/root"), eq(List.of()));
        assertEquals("session-2", recordField(rollingRecord, "unitId"));
        assertEquals(created.getSlices().getFirst().getId(), recordField(rollingRecord, "sliceId"));
        assertEquals("fresh-summary", recordField(rollingRecord, "summary"));
    }

    @Test
    void shouldTrimPersistedOverlapFromCurrentSnapshot() throws Exception {
        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-3");
        MemoryUpdater updater = new MemoryUpdater();
        setField(updater, "memoryCapability", memoryCapability);

        MemoryUnit existingUnit = Mockito.mock(MemoryUnit.class);
        when(existingUnit.getConversationMessages()).thenReturn(List.of(
                message(Message.Character.USER, "m1"),
                message(Message.Character.ASSISTANT, "m2"),
                message(Message.Character.USER, "m3"),
                message(Message.Character.ASSISTANT, "m4")
        ));
        memoryCapability.putUnit("session-3", existingUnit);

        List<Message> increment = invokeResolveChatIncrement(
                updater,
                List.of(
                        message(Message.Character.USER, "m3"),
                        message(Message.Character.ASSISTANT, "m4"),
                        message(Message.Character.USER, "m5"),
                        message(Message.Character.ASSISTANT, "m6")
                )
        );

        assertEquals(List.of("m5", "m6"), increment.stream().map(Message::getContent).toList());
    }

    @Test
    void shouldReturnEmptyIncrementWhenSnapshotIsFullyPersisted() throws Exception {
        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-4");
        MemoryUpdater updater = new MemoryUpdater();
        setField(updater, "memoryCapability", memoryCapability);

        MemoryUnit existingUnit = Mockito.mock(MemoryUnit.class);
        when(existingUnit.getConversationMessages()).thenReturn(List.of(
                message(Message.Character.USER, "m1"),
                message(Message.Character.ASSISTANT, "m2"),
                message(Message.Character.USER, "m3")
        ));
        memoryCapability.putUnit("session-4", existingUnit);

        List<Message> increment = invokeResolveChatIncrement(
                updater,
                List.of(
                        message(Message.Character.ASSISTANT, "m2"),
                        message(Message.Character.USER, "m3")
                )
        );

        assertEquals(List.of(), increment);
    }

    @Test
    void shouldReturnNullWhenUpdateMemoryReceivesEmptySnapshot() throws Exception {
        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-5");
        MemoryUpdater updater = new MemoryUpdater();
        MemoryRuntime memoryRuntime = Mockito.mock(MemoryRuntime.class);
        setField(updater, "memoryCapability", memoryCapability);
        setField(updater, "memoryRuntime", memoryRuntime);

        Object rollingRecord = invokeUpdateMemory(updater, List.of());

        assertNull(rollingRecord);
        assertNull(memoryCapability.lastSummary());
        Mockito.verifyNoInteractions(memoryRuntime);
    }

    private static final class StubMemoryCapability implements MemoryCapability {
        private final String sessionId;
        private final Map<String, MemoryUnit> units = new HashMap<>();
        private List<Message> lastChatMessages;
        private String lastSummary;

        private StubMemoryCapability(String sessionId) {
            this.sessionId = sessionId;
        }

        private void putUnit(String unitId, MemoryUnit memoryUnit) {
            units.put(unitId, memoryUnit);
        }

        private void putUnit(MemoryUnit memoryUnit) {
            units.put(memoryUnit.getId(), memoryUnit);
        }

        private List<Message> lastChatMessages() {
            return lastChatMessages;
        }

        private String lastSummary() {
            return lastSummary;
        }

        @Override
        public MemoryUnit getMemoryUnit(String unitId) {
            return units.get(unitId);
        }

        @Override
        public Result<MemorySlice> getMemorySlice(String unitId, String sliceId) {
            MemoryUnit unit = units.get(unitId);
            if (unit == null || unit.getSlices() == null) {
                return Result.failure(new MemoryLookupException(
                        "Memory slice not found: " + unitId + ":" + sliceId,
                        unitId + ":" + sliceId,
                        "MEMORY_SLICE"
                ));
            }
            return unit.getSlices().stream()
                    .filter(slice -> sliceId.equals(slice.getId()))
                    .findFirst()
                    .map(Result::success)
                    .orElseGet(() -> Result.failure(new MemoryLookupException(
                            "Memory slice not found: " + unitId + ":" + sliceId,
                            unitId + ":" + sliceId,
                            "MEMORY_SLICE"
                    )));
        }

        @Override
        public MemoryUnit updateMemoryUnit(List<Message> chatMessages, String summary) {
            lastChatMessages = List.copyOf(chatMessages);
            lastSummary = summary;
            MemoryUnit unit = units.computeIfAbsent(sessionId, MemoryUnit::new);
            unit.updateTimestamp();
            int startIndex = unit.getConversationMessages().size();
            unit.getConversationMessages().addAll(chatMessages);
            unit.getSlices().add(new MemorySlice(startIndex, startIndex + chatMessages.size(), summary));
            return unit;
        }

        @Override
        public Collection<MemoryUnit> listMemoryUnits() {
            return units.values();
        }

        @Override
        public void refreshMemorySession() {
        }

        @Override
        public String getMemorySessionId() {
            return sessionId;
        }
    }
}
