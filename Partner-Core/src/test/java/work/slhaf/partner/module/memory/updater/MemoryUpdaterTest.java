package work.slhaf.partner.module.memory.updater;

import org.junit.jupiter.api.Test;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.module.memory.updater.summarizer.entity.SummarizeResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MemoryUpdaterTest {

    private static MemoryUnit invokeBuildMemoryUnit(MemoryUpdater updater,
                                                    List<Message> chatMessages,
                                                    SummarizeResult summarizeResult) throws Exception {
        Method method = MemoryUpdater.class.getDeclaredMethod("buildMemoryUnit", List.class, SummarizeResult.class);
        method.setAccessible(true);
        return (MemoryUnit) method.invoke(updater, chatMessages, summarizeResult);
    }

    @SuppressWarnings("unchecked")
    private static List<Message> invokeResolveChatIncrement(MemoryUpdater updater,
                                                            List<Message> chatMessages) throws Exception {
        Method method = MemoryUpdater.class.getDeclaredMethod("resolveChatIncrement", List.class);
        method.setAccessible(true);
        return (List<Message>) method.invoke(updater, chatMessages);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static SummarizeResult summarizeResult(String summary) {
        SummarizeResult result = new SummarizeResult();
        result.setSummary(summary);
        return result;
    }

    private static Message message(Message.Character role, String content) {
        return new Message(role, content);
    }

    @Test
    void shouldAppendNewSliceToExistingMemoryUnitWithinSameSession() throws Exception {
        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-1");
        MemoryUpdater updater = new MemoryUpdater();
        setField(updater, "memoryCapability", memoryCapability);

        String sessionId = memoryCapability.getMemorySessionId();
        MemoryUnit existingUnit = new MemoryUnit();
        existingUnit.setId(sessionId);
        existingUnit.setConversationMessages(new ArrayList<>(List.of(
                message(Message.Character.USER, "old-user"),
                message(Message.Character.ASSISTANT, "old-assistant")
        )));
        MemorySlice existingSlice = new MemorySlice();
        existingSlice.setId("slice-1");
        existingSlice.setStartIndex(0);
        existingSlice.setEndIndex(2);
        existingSlice.setSummary("old-summary");
        existingSlice.setTimestamp(1L);
        existingUnit.setSlices(new ArrayList<>(List.of(existingSlice)));
        memoryCapability.saveMemoryUnit(existingUnit);

        MemoryUnit merged = invokeBuildMemoryUnit(
                updater,
                List.of(
                        message(Message.Character.USER, "new-user"),
                        message(Message.Character.ASSISTANT, "new-assistant")
                ),
                summarizeResult("new-summary")
        );

        assertEquals(sessionId, merged.getId());
        assertEquals(4, merged.getConversationMessages().size());
        assertEquals(List.of("old-user", "old-assistant", "new-user", "new-assistant"),
                merged.getConversationMessages().stream().map(Message::getContent).toList());
        assertEquals(2, merged.getSlices().size());

        MemorySlice appendedSlice = merged.getSlices().get(1);
        assertNotNull(appendedSlice.getId());
        assertEquals(2, appendedSlice.getStartIndex());
        assertEquals(4, appendedSlice.getEndIndex());
        assertEquals("new-summary", appendedSlice.getSummary());
    }

    @Test
    void shouldCreateNewMemoryUnitForNewSessionId() throws Exception {
        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-2");
        MemoryUpdater updater = new MemoryUpdater();
        setField(updater, "memoryCapability", memoryCapability);

        MemoryUnit created = invokeBuildMemoryUnit(
                updater,
                List.of(
                        message(Message.Character.USER, "first"),
                        message(Message.Character.ASSISTANT, "second")
                ),
                summarizeResult("fresh-summary")
        );

        assertEquals("session-2", created.getId());
        assertEquals(2, created.getConversationMessages().size());
        assertEquals(1, created.getSlices().size());
        assertEquals(0, created.getSlices().getFirst().getStartIndex());
        assertEquals(2, created.getSlices().getFirst().getEndIndex());
        assertEquals("fresh-summary", created.getSlices().getFirst().getSummary());
    }

    @Test
    void shouldTrimPersistedOverlapFromCurrentSnapshot() throws Exception {
        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-3");
        MemoryUpdater updater = new MemoryUpdater();
        setField(updater, "memoryCapability", memoryCapability);

        MemoryUnit existingUnit = new MemoryUnit();
        existingUnit.setId("session-3");
        existingUnit.setConversationMessages(new ArrayList<>(List.of(
                message(Message.Character.USER, "m1"),
                message(Message.Character.ASSISTANT, "m2"),
                message(Message.Character.USER, "m3"),
                message(Message.Character.ASSISTANT, "m4")
        )));
        memoryCapability.saveMemoryUnit(existingUnit);

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

        MemoryUnit existingUnit = new MemoryUnit();
        existingUnit.setId("session-4");
        existingUnit.setConversationMessages(new ArrayList<>(List.of(
                message(Message.Character.USER, "m1"),
                message(Message.Character.ASSISTANT, "m2"),
                message(Message.Character.USER, "m3")
        )));
        memoryCapability.saveMemoryUnit(existingUnit);

        List<Message> increment = invokeResolveChatIncrement(
                updater,
                List.of(
                        message(Message.Character.ASSISTANT, "m2"),
                        message(Message.Character.USER, "m3")
                )
        );

        assertEquals(List.of(), increment);
    }

    private static final class StubMemoryCapability implements MemoryCapability {
        private final String sessionId;
        private final Map<String, MemoryUnit> units = new HashMap<>();

        private StubMemoryCapability(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public void saveMemoryUnit(MemoryUnit memoryUnit) {
            units.put(memoryUnit.getId(), memoryUnit);
        }

        @Override
        public MemoryUnit getMemoryUnit(String unitId) {
            return units.get(unitId);
        }

        @Override
        public MemorySlice getMemorySlice(String unitId, String sliceId) {
            MemoryUnit unit = units.get(unitId);
            if (unit == null || unit.getSlices() == null) {
                return null;
            }
            return unit.getSlices().stream()
                    .filter(slice -> sliceId.equals(slice.getId()))
                    .findFirst()
                    .orElse(null);
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
