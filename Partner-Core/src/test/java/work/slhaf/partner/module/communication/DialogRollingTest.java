package work.slhaf.partner.module.communication;

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
import work.slhaf.partner.module.memory.updater.summarizer.MultiSummarizer;
import work.slhaf.partner.module.memory.updater.summarizer.SingleSummarizer;
import work.slhaf.partner.module.memory.updater.summarizer.entity.SummarizeResult;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class DialogRollingTest {

    @BeforeAll
    static void beforeAll(@TempDir Path tempDir) {
        System.setProperty("user.home", tempDir.toAbsolutePath().toString());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Message message(Message.Character role, String content) {
        return new Message(role, content);
    }

    private static SummarizeResult summarizeResult(String summary, String topicPath, List<String> relatedTopicPath) {
        SummarizeResult result = new SummarizeResult();
        result.setSummary(summary);
        result.setTopicPath(topicPath);
        result.setRelatedTopicPath(relatedTopicPath);
        return result;
    }

    @Test
    void shouldDelegateMemoryUpdateToCapability() throws Exception {
        String sessionId = "dialog-rolling-" + UUID.randomUUID();
        StubMemoryCapability memoryCapability = new StubMemoryCapability(sessionId);
        DialogRolling dialogRolling = new DialogRolling();
        MemoryRuntime memoryRuntime = Mockito.mock(MemoryRuntime.class);
        MultiSummarizer multiSummarizer = Mockito.mock(MultiSummarizer.class);
        SingleSummarizer singleSummarizer = Mockito.mock(SingleSummarizer.class);
        setField(dialogRolling, "memoryCapability", memoryCapability);
        setField(dialogRolling, "memoryRuntime", memoryRuntime);
        setField(dialogRolling, "multiSummarizer", multiSummarizer);
        setField(dialogRolling, "singleSummarizer", singleSummarizer);

        when(memoryRuntime.getTopicTree()).thenReturn("topic-tree");
        when(multiSummarizer.execute(Mockito.any())).thenReturn(Result.success(
                summarizeResult("new-summary", "topic/main", List.of("topic/related"))
        ));

        MemoryUnit existingUnit = new MemoryUnit(sessionId);
        existingUnit.getConversationMessages().addAll(List.of(
                message(Message.Character.USER, "old-user"),
                message(Message.Character.ASSISTANT, "old-assistant")
        ));
        existingUnit.getSlices().add(MemorySlice.restore("slice-1", 0, 2, "old-summary", 1L));
        memoryCapability.putUnit(existingUnit);

        RollingResult rollingResult = dialogRolling.buildRollingResult(List.of(
                message(Message.Character.USER, "new-user"),
                message(Message.Character.ASSISTANT, "new-assistant")
        ), 4, 6);

        MemoryUnit merged = memoryCapability.getMemoryUnit(sessionId);
        assertEquals(List.of("old-user", "old-assistant", "new-user", "new-assistant"),
                merged.getConversationMessages().stream().map(Message::getContent).toList());
        assertEquals(2, merged.getSlices().size());

        MemorySlice appendedSlice = merged.getSlices().getLast();
        assertNotNull(appendedSlice.getId());
        assertEquals(2, appendedSlice.getStartIndex());
        assertEquals(4, appendedSlice.getEndIndex());
        assertEquals("new-summary", appendedSlice.getSummary());
        assertEquals(sessionId, rollingResult.memoryUnit().getId());
        assertEquals(appendedSlice.getId(), rollingResult.memorySlice().getId());
        assertEquals("new-summary", rollingResult.summary());
    }

    @Test
    void shouldCreateFirstSliceForFreshSessionThroughCapability() throws Exception {
        String sessionId = "dialog-rolling-" + UUID.randomUUID();
        StubMemoryCapability memoryCapability = new StubMemoryCapability(sessionId);
        DialogRolling dialogRolling = new DialogRolling();
        MemoryRuntime memoryRuntime = Mockito.mock(MemoryRuntime.class);
        MultiSummarizer multiSummarizer = Mockito.mock(MultiSummarizer.class);
        SingleSummarizer singleSummarizer = Mockito.mock(SingleSummarizer.class);
        setField(dialogRolling, "memoryCapability", memoryCapability);
        setField(dialogRolling, "memoryRuntime", memoryRuntime);
        setField(dialogRolling, "multiSummarizer", multiSummarizer);
        setField(dialogRolling, "singleSummarizer", singleSummarizer);

        when(memoryRuntime.getTopicTree()).thenReturn("topic-tree");
        when(multiSummarizer.execute(Mockito.any())).thenReturn(Result.success(
                summarizeResult("fresh-summary", "topic/root", List.of())
        ));

        RollingResult rollingResult = dialogRolling.buildRollingResult(List.of(
                message(Message.Character.USER, "first"),
                message(Message.Character.ASSISTANT, "second")
        ), 2, 6);

        MemoryUnit created = memoryCapability.getMemoryUnit(sessionId);
        assertNotNull(created);
        assertEquals(List.of("first", "second"),
                created.getConversationMessages().stream().map(Message::getContent).toList());
        assertEquals(1, created.getSlices().size());
        assertEquals(0, created.getSlices().getFirst().getStartIndex());
        assertEquals(2, created.getSlices().getFirst().getEndIndex());
        assertEquals("fresh-summary", created.getSlices().getFirst().getSummary());
        assertEquals(created, rollingResult.memoryUnit());
    }

    @Test
    void shouldTrimPersistedOverlapFromCurrentSnapshot() throws Exception {
        String sessionId = "dialog-rolling-" + UUID.randomUUID();
        StubMemoryCapability memoryCapability = new StubMemoryCapability(sessionId);
        DialogRolling dialogRolling = new DialogRolling();
        setField(dialogRolling, "memoryCapability", memoryCapability);

        MemoryUnit existingUnit = Mockito.mock(MemoryUnit.class);
        when(existingUnit.getConversationMessages()).thenReturn(List.of(
                message(Message.Character.USER, "m1"),
                message(Message.Character.ASSISTANT, "m2"),
                message(Message.Character.USER, "m3"),
                message(Message.Character.ASSISTANT, "m4")
        ));
        memoryCapability.putUnit(sessionId, existingUnit);

        List<Message> increment = dialogRolling.resolveChatIncrement(List.of(
                message(Message.Character.USER, "m3"),
                message(Message.Character.ASSISTANT, "m4"),
                message(Message.Character.USER, "m5"),
                message(Message.Character.ASSISTANT, "m6")
        ));

        assertEquals(List.of("m5", "m6"), increment.stream().map(Message::getContent).toList());
    }

    @Test
    void shouldFallbackWhenSummarizeResultIsBlank() throws Exception {
        String sessionId = "dialog-rolling-" + UUID.randomUUID();
        StubMemoryCapability memoryCapability = new StubMemoryCapability(sessionId);
        DialogRolling dialogRolling = new DialogRolling();
        MemoryRuntime memoryRuntime = Mockito.mock(MemoryRuntime.class);
        MultiSummarizer multiSummarizer = Mockito.mock(MultiSummarizer.class);
        SingleSummarizer singleSummarizer = Mockito.mock(SingleSummarizer.class);
        setField(dialogRolling, "memoryCapability", memoryCapability);
        setField(dialogRolling, "memoryRuntime", memoryRuntime);
        setField(dialogRolling, "multiSummarizer", multiSummarizer);
        setField(dialogRolling, "singleSummarizer", singleSummarizer);

        when(memoryRuntime.getTopicTree()).thenReturn("topic-tree");
        when(multiSummarizer.execute(Mockito.any())).thenReturn(Result.success(summarizeResult("   ", "topic/root", List.of())));

        RollingResult rollingResult = dialogRolling.buildRollingResult(List.of(
                message(Message.Character.USER, "u1"),
                message(Message.Character.ASSISTANT, "a1")
        ), 2, 6);

        assertEquals(sessionId, rollingResult.memoryUnit().getId());
        assertEquals("no summary, due to empty summarize result", rollingResult.summary());
    }

    private static final class StubMemoryCapability implements MemoryCapability {
        private final String sessionId;
        private final Map<String, MemoryUnit> units = new HashMap<>();

        private StubMemoryCapability(String sessionId) {
            this.sessionId = sessionId;
        }

        private void putUnit(String unitId, MemoryUnit memoryUnit) {
            units.put(unitId == null ? memoryUnit.getId() : unitId, memoryUnit);
        }

        private void putUnit(MemoryUnit memoryUnit) {
            units.put(memoryUnit.getId(), memoryUnit);
        }

        @Override
        public MemoryUnit getMemoryUnit(String unitId) {
            return units.get(unitId);
        }

        @Override
        public work.slhaf.partner.framework.agent.support.Result<MemorySlice> getMemorySlice(String unitId, String sliceId) {
            return null;
        }

        @Override
        public MemoryUnit updateMemoryUnit(List<Message> chatMessages, String summary) {
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
