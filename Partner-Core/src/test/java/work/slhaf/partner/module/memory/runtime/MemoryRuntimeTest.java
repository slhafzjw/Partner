package work.slhaf.partner.module.memory.runtime;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.core.memory.pojo.SliceRef;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.memory.runtime.exception.MemoryLookupException;
import work.slhaf.partner.module.memory.selector.ActivatedMemorySlice;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRuntimeTest {

    @BeforeAll
    public static void beforeAll(@TempDir Path tempDir) {
        System.setProperty("user.home", tempDir.toAbsolutePath().toString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, CopyOnWriteArrayList<SliceRef>> topicSlices(MemoryRuntime runtime) throws Exception {
        Field field = MemoryRuntime.class.getDeclaredField("topicSlices");
        field.setAccessible(true);
        return (Map<String, CopyOnWriteArrayList<SliceRef>>) field.get(runtime);
    }

    @SuppressWarnings("unchecked")
    private static List<Message> invokeSliceMessages(MemoryRuntime runtime, MemoryUnit unit, MemorySlice slice) throws Exception {
        Method method = MemoryRuntime.class.getDeclaredMethod("sliceMessages", MemoryUnit.class, MemorySlice.class);
        method.setAccessible(true);
        return (List<Message>) method.invoke(runtime, unit, slice);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Message message(String content) {
        return new Message(Message.Character.USER, content);
    }

    private static CognitionCapability stubCognitionCapability(List<Message> chatMessages) {
        Lock lock = new ReentrantLock();
        return new CognitionCapability() {
            @Override
            public void initiateTurn(String input, String target, String... skippedModules) {
            }

            @Override
            public work.slhaf.partner.core.cognition.ContextWorkspace contextWorkspace() {
                return new work.slhaf.partner.core.cognition.ContextWorkspace();
            }

            @Override
            public List<Message> getChatMessages() {
                return chatMessages;
            }

            @Override
            public List<Message> snapshotChatMessages() {
                return chatMessages;
            }

            @Override
            public void rollChatMessagesWithSnapshot(int snapshotSize, int retainDivisor) {
            }

            @Override
            public void refreshRecentChatMessagesContext() {

            }

            @Override
            public Element messageNotesElement() {
                return null;
            }

            @Override
            public Lock getMessageLock() {
                return lock;
            }
        };
    }

    @Test
    void shouldSliceMessagesUsingLeftClosedRightOpenRange() throws Exception {
        MemoryRuntime runtime = new MemoryRuntime();
        MemoryUnit unit = new MemoryUnit("unit-1");
        unit.getConversationMessages().addAll(List.of(
                message("m0"),
                message("m1"),
                message("m2"),
                message("m3")
        ));

        MemorySlice slice = MemorySlice.restore("slice-1", 1, 3, null, 1L);

        List<Message> messages = invokeSliceMessages(runtime, unit, slice);
        assertEquals(List.of("m1", "m2"), messages.stream().map(Message::getContent).toList());

        MemorySlice emptySlice = MemorySlice.restore("slice-2", 2, 2, null, 2L);
        assertTrue(invokeSliceMessages(runtime, unit, emptySlice).isEmpty());
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void shouldBindTopicToLatestMemorySliceInsteadOfFirstSlice() throws Exception {
        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-test");
        MemoryRuntime runtime = new MemoryRuntime();
        setField(runtime, "memoryCapability", memoryCapability);
        setField(runtime, "cognitionCapability", stubCognitionCapability(List.of(message("seed"))));

        MemoryUnit unit = new MemoryUnit("unit-99");
        unit.getConversationMessages().addAll(List.of(
                message("m0"),
                message("m1"),
                message("m2"),
                message("m3")
        ));

        MemorySlice firstSlice = MemorySlice.restore("slice-1", 0, 2, "first", 1L);

        MemorySlice secondSlice = MemorySlice.restore("slice-2", 2, 4, "second", 2L);

        unit.getSlices().addAll(List.of(firstSlice, secondSlice));
        memoryCapability.remember(unit);

        runtime.recordMemory(unit, "topic/main", List.of("topic/related"));

        Map<String, CopyOnWriteArrayList<SliceRef>> topicSlices = topicSlices(runtime);
        assertEquals(List.of("slice-2"),
                topicSlices.get("topic/main").stream().map(SliceRef::getSliceId).toList());
        assertEquals(List.of("slice-2"),
                topicSlices.get("topic/related").stream().map(SliceRef::getSliceId).toList());
    }

    @Test
    void shouldRoundTripTopicAndDateIndexesViaState() throws Exception {
        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-test");
        MemoryRuntime runtime = new MemoryRuntime();
        setField(runtime, "memoryCapability", memoryCapability);
        setField(runtime, "cognitionCapability", stubCognitionCapability(List.of(message("seed"))));

        MemoryUnit unit = new MemoryUnit("unit-100");
        unit.getConversationMessages().addAll(List.of(
                message("m0"),
                message("m1"),
                message("m2"),
                message("m3")
        ));
        MemorySlice firstSlice = MemorySlice.restore("slice-1", 0, 2, "first", 86_400_000L);
        MemorySlice secondSlice = MemorySlice.restore("slice-2", 2, 4, "second", 172_800_000L);
        unit.getSlices().addAll(List.of(firstSlice, secondSlice));
        memoryCapability.remember(unit);

        runtime.recordMemory(unit, "topic/main", List.of("topic/related"));

        JSONObject state = JSONObject.parseObject(runtime.convert().toString());
        JSONArray topicSlices = state.getJSONArray("topic_slices");
        assertEquals(2, topicSlices.size());
        JSONObject mainTopic = topicSlices.stream()
                .map(JSONObject.class::cast)
                .filter(item -> "topic/main".equals(item.getString("topic_path")))
                .findFirst()
                .orElseThrow();
        assertEquals("slice-2", mainTopic.getJSONArray("refs").getJSONObject(0).getString("slice_id"));

        JSONArray dateIndex = state.getJSONArray("date_index");
        assertEquals(2, dateIndex.size());
        JSONObject secondDate = dateIndex.stream()
                .map(JSONObject.class::cast)
                .filter(item -> "1970-01-03".equals(item.getString("date")))
                .findFirst()
                .orElseThrow();
        assertEquals("slice-2", secondDate.getJSONArray("refs").getJSONObject(0).getString("slice_id"));

        MemoryRuntime restored = new MemoryRuntime();
        setField(restored, "memoryCapability", memoryCapability);
        setField(restored, "cognitionCapability", stubCognitionCapability(List.of(message("seed"))));
        restored.load(state);

        List<ActivatedMemorySlice> topicResult = restored.queryActivatedMemoryByTopicPath("topic/main");
        assertEquals(1, topicResult.size());
        assertEquals("slice-2", topicResult.getFirst().getSliceId());
        assertEquals(List.of("m2", "m3"), topicResult.getFirst().getMessages().stream().map(Message::getContent).toList());

        List<ActivatedMemorySlice> dateResult = restored.queryActivatedMemoryByDate(LocalDate.parse("1970-01-03"));
        assertEquals(1, dateResult.size());
        assertEquals("slice-2", dateResult.getFirst().getSliceId());
        assertEquals("second", dateResult.getFirst().getSummary());
    }

    @Test
    void shouldReturnEmptyActivatedMemoryWhenTopicOrDateIndexDoesNotExist() throws Exception {
        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-test");
        MemoryRuntime runtime = new MemoryRuntime();
        setField(runtime, "memoryCapability", memoryCapability);
        setField(runtime, "cognitionCapability", stubCognitionCapability(List.of(message("seed"))));

        assertTrue(runtime.queryActivatedMemoryByTopicPath("topic/missing").isEmpty());
        assertTrue(runtime.queryActivatedMemoryByDate(LocalDate.parse("1970-01-01")).isEmpty());
    }

    private static final class StubMemoryCapability implements MemoryCapability {
        private final String sessionId;
        private final Map<String, MemoryUnit> units = new HashMap<>();

        private StubMemoryCapability(String sessionId) {
            this.sessionId = sessionId;
        }

        private void remember(MemoryUnit unit) {
            units.put(unit.getId(), unit);
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
            return null;
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
