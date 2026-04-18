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
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.memory.pojo.ActivationProfile;
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRuntimeTest {

    private static final ActivationProfile DEFAULT_PROFILE = new ActivationProfile(0.55f, 0.35f, 0.50f);

    @BeforeAll
    public static void beforeAll(@TempDir Path tempDir) {
        System.setProperty("user.home", tempDir.toAbsolutePath().toString());
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

        runtime.recordMemory(unit, "topic/main", List.of("topic/related"), DEFAULT_PROFILE);

        List<ActivatedMemorySlice> topicResult = runtime.queryActivatedMemoryByTopicPath("topic/main");
        assertEquals(List.of("slice-2"), topicResult.stream().map(ActivatedMemorySlice::getSliceId).toList());
        assertTrue(runtime.getTopicTree().contains("topic/main [root]"));
        assertTrue(runtime.getTopicTree().contains("topic/related [root]"));
        assertTrue(JSONObject.parseObject(runtime.convert().toString())
                .getJSONArray("topic_slices")
                .stream()
                .map(JSONObject.class::cast)
                .anyMatch(item -> "topic/main".equals(item.getString("topic_path"))));
    }

    @Test
    void shouldExpandTopicQueryToLatestRelatedTopicMemory() throws Exception {
        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-test");
        MemoryRuntime runtime = new MemoryRuntime();
        setField(runtime, "memoryCapability", memoryCapability);
        setField(runtime, "cognitionCapability", stubCognitionCapability(List.of(message("seed"))));

        MemoryUnit mainUnit = new MemoryUnit("unit-main");
        mainUnit.getConversationMessages().addAll(List.of(
                message("m0"),
                message("m1"),
                message("m2"),
                message("m3")
        ));
        MemorySlice mainSlice = MemorySlice.restore("slice-main", 0, 2, "main", 86_400_000L);
        mainUnit.getSlices().add(mainSlice);
        memoryCapability.remember(mainUnit);

        MemoryUnit relatedUnit = new MemoryUnit("unit-related");
        relatedUnit.getConversationMessages().addAll(List.of(
                message("r0"),
                message("r1")
        ));
        MemorySlice relatedSlice = MemorySlice.restore("slice-related", 0, 2, "related", 172_800_000L);
        relatedUnit.getSlices().add(relatedSlice);
        memoryCapability.remember(relatedUnit);

        runtime.recordMemory(mainUnit, "topic/main", List.of("topic/related"), DEFAULT_PROFILE);
        runtime.recordMemory(relatedUnit, "topic/related", List.of(), DEFAULT_PROFILE);

        List<ActivatedMemorySlice> topicResult = runtime.queryActivatedMemoryByTopicPath("topic/main");
        assertEquals(List.of("slice-main", "slice-related"),
                topicResult.stream().map(ActivatedMemorySlice::getSliceId).toList());
    }

    @Test
    void shouldIndexDateIncrementallyWithoutRebuildingWholeUnit() throws Exception {
        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-test");
        MemoryRuntime runtime = new MemoryRuntime();
        setField(runtime, "memoryCapability", memoryCapability);
        setField(runtime, "cognitionCapability", stubCognitionCapability(List.of(message("seed"))));

        MemoryUnit firstUnitSnapshot = new MemoryUnit("unit-100");
        firstUnitSnapshot.getConversationMessages().addAll(List.of(message("m0"), message("m1")));
        MemorySlice firstSlice = MemorySlice.restore("slice-1", 0, 1, "first", 86_400_000L);
        firstUnitSnapshot.getSlices().add(firstSlice);
        memoryCapability.remember(firstUnitSnapshot);
        runtime.recordMemory(firstUnitSnapshot, "topic/main", List.of(), DEFAULT_PROFILE);

        firstUnitSnapshot.getConversationMessages().clear();
        firstUnitSnapshot.getConversationMessages().addAll(List.of(message("m2"), message("m3")));
        MemorySlice secondSlice = MemorySlice.restore("slice-2", 0, 1, "second", 172_800_000L);
        firstUnitSnapshot.getSlices().clear();
        firstUnitSnapshot.getSlices().add(secondSlice);
        memoryCapability.remember(firstUnitSnapshot);
        runtime.recordMemory(firstUnitSnapshot, "topic/main", List.of(), DEFAULT_PROFILE);

        JSONObject state = JSONObject.parseObject(runtime.convert().toString());
        JSONArray dateIndex = state.getJSONArray("date_index");
        JSONObject firstDate = dateIndex.stream()
                .map(JSONObject.class::cast)
                .filter(item -> "1970-01-02".equals(item.getString("date")))
                .findFirst()
                .orElseThrow();
        JSONObject secondDate = dateIndex.stream()
                .map(JSONObject.class::cast)
                .filter(item -> "1970-01-03".equals(item.getString("date")))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("slice-1"),
                firstDate.getJSONArray("refs").toJavaList(JSONObject.class).stream().map(obj -> obj.getString("slice_id")).toList());
        assertEquals(List.of("slice-2"),
                secondDate.getJSONArray("refs").toJavaList(JSONObject.class).stream().map(obj -> obj.getString("slice_id")).toList());
    }

    @Test
    void shouldRoundTripTopicAndDateIndexesViaState() throws Exception {
        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-test");
        MemoryRuntime runtime = new MemoryRuntime();
        setField(runtime, "memoryCapability", memoryCapability);
        setField(runtime, "cognitionCapability", stubCognitionCapability(List.of(message("seed"))));

        MemoryUnit mainUnit = new MemoryUnit("unit-200");
        mainUnit.getConversationMessages().addAll(List.of(
                message("m0"),
                message("m1"),
                message("m2"),
                message("m3")
        ));
        MemorySlice firstSlice = MemorySlice.restore("slice-1", 0, 2, "first", 86_400_000L);
        MemorySlice secondSlice = MemorySlice.restore("slice-2", 2, 4, "second", 172_800_000L);
        mainUnit.getSlices().addAll(List.of(firstSlice, secondSlice));
        memoryCapability.remember(mainUnit);
        runtime.recordMemory(mainUnit, "topic/main", List.of("topic/related"), DEFAULT_PROFILE);

        MemoryUnit relatedUnit = new MemoryUnit("unit-201");
        relatedUnit.getConversationMessages().addAll(List.of(message("r0"), message("r1")));
        MemorySlice relatedSlice = MemorySlice.restore("slice-3", 0, 2, "related", 259_200_000L);
        relatedUnit.getSlices().add(relatedSlice);
        memoryCapability.remember(relatedUnit);
        runtime.recordMemory(relatedUnit, "topic/related", List.of(), DEFAULT_PROFILE);

        JSONObject state = JSONObject.parseObject(runtime.convert().toString());
        JSONArray topicSlices = state.getJSONArray("topic_slices");
        assertEquals(2, topicSlices.size());
        JSONObject mainTopic = topicSlices.stream()
                .map(JSONObject.class::cast)
                .filter(item -> "topic/main".equals(item.getString("topic_path")))
                .findFirst()
                .orElseThrow();
        JSONObject binding = mainTopic.getJSONArray("bindings").getJSONObject(0);
        assertEquals("slice-2", binding.getString("slice_id"));
        assertEquals(172_800_000L, binding.getLongValue("timestamp"));
        assertEquals(List.of("topic/related"), binding.getJSONArray("related_topic_paths").toJavaList(String.class));
        JSONObject activationProfile = binding.getJSONObject("activation_profile");
        assertEquals(0.55f, activationProfile.getFloatValue("activation_weight"));
        assertEquals(0.35f, activationProfile.getFloatValue("diffusion_weight"));
        assertEquals(0.50f, activationProfile.getFloatValue("context_independence_weight"));

        JSONArray dateIndex = state.getJSONArray("date_index");
        assertEquals(2, dateIndex.size());
        JSONObject thirdDate = dateIndex.stream()
                .map(JSONObject.class::cast)
                .filter(item -> "1970-01-04".equals(item.getString("date")))
                .findFirst()
                .orElseThrow();
        assertEquals("slice-3", thirdDate.getJSONArray("refs").getJSONObject(0).getString("slice_id"));

        MemoryRuntime restored = new MemoryRuntime();
        setField(restored, "memoryCapability", memoryCapability);
        setField(restored, "cognitionCapability", stubCognitionCapability(List.of(message("seed"))));
        restored.load(state);

        List<ActivatedMemorySlice> topicResult = restored.queryActivatedMemoryByTopicPath("topic/main");
        assertEquals(List.of("slice-2", "slice-3"),
                topicResult.stream().map(ActivatedMemorySlice::getSliceId).toList());
        assertEquals(List.of("m2", "m3"), topicResult.getFirst().getMessages().stream().map(Message::getContent).toList());

        List<ActivatedMemorySlice> dateResult = restored.queryActivatedMemoryByDate(LocalDate.parse("1970-01-04"));
        assertEquals(1, dateResult.size());
        assertEquals("slice-3", dateResult.getFirst().getSliceId());
        assertEquals("related", dateResult.getFirst().getSummary());
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

    @Test
    void shouldRankTopicMatchesBySourceAndActivationProfile() throws Exception {
        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-test");
        MemoryRuntime runtime = new MemoryRuntime();
        setField(runtime, "memoryCapability", memoryCapability);
        setField(runtime, "cognitionCapability", stubCognitionCapability(List.of(message("seed"))));

        MemoryUnit primaryUnit = new MemoryUnit("unit-primary");
        primaryUnit.getConversationMessages().addAll(List.of(message("p0"), message("p1")));
        MemorySlice primarySlice = MemorySlice.restore("slice-primary", 0, 2, "primary", System.currentTimeMillis());
        primaryUnit.getSlices().add(primarySlice);
        memoryCapability.remember(primaryUnit);
        runtime.recordMemory(primaryUnit, "topic->main", List.of("topic->related"), new ActivationProfile(0.9f, 0.1f, 0.9f));

        MemoryUnit relatedUnit = new MemoryUnit("unit-related-rank");
        relatedUnit.getConversationMessages().addAll(List.of(message("r0"), message("r1")));
        MemorySlice relatedSlice = MemorySlice.restore("slice-related-rank", 0, 2, "related", System.currentTimeMillis());
        relatedUnit.getSlices().add(relatedSlice);
        memoryCapability.remember(relatedUnit);
        runtime.recordMemory(relatedUnit, "topic->related", List.of(), new ActivationProfile(1.0f, 1.0f, 1.0f));

        MemoryUnit parentUnit = new MemoryUnit("unit-parent");
        parentUnit.getConversationMessages().addAll(List.of(message("x0"), message("x1")));
        MemorySlice parentSlice = MemorySlice.restore("slice-parent", 0, 2, "parent", System.currentTimeMillis());
        parentUnit.getSlices().add(parentSlice);
        memoryCapability.remember(parentUnit);
        runtime.recordMemory(parentUnit, "topic", List.of(), new ActivationProfile(1.0f, 1.0f, 1.0f));

        List<ActivatedMemorySlice> topicResult = runtime.queryActivatedMemoryByTopicPath("topic->main");
        assertEquals(List.of("slice-primary", "slice-related-rank", "slice-parent"),
                topicResult.stream().map(ActivatedMemorySlice::getSliceId).toList());
    }

    @Test
    void shouldNotExpandRelatedTopicWhenDiffusionWeightIsZero() throws Exception {
        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-test");
        MemoryRuntime runtime = new MemoryRuntime();
        setField(runtime, "memoryCapability", memoryCapability);
        setField(runtime, "cognitionCapability", stubCognitionCapability(List.of(message("seed"))));

        MemoryUnit primaryUnit = new MemoryUnit("unit-primary-zero");
        primaryUnit.getConversationMessages().addAll(List.of(message("p0"), message("p1")));
        MemorySlice primarySlice = MemorySlice.restore("slice-primary-zero", 0, 2, "primary", System.currentTimeMillis());
        primaryUnit.getSlices().add(primarySlice);
        memoryCapability.remember(primaryUnit);
        runtime.recordMemory(
                primaryUnit,
                "topic->main",
                List.of("topic->related"),
                new ActivationProfile(0.8f, 0.0f, 0.8f)
        );

        MemoryUnit relatedUnit = new MemoryUnit("unit-related-zero");
        relatedUnit.getConversationMessages().addAll(List.of(message("r0"), message("r1")));
        MemorySlice relatedSlice = MemorySlice.restore("slice-related-zero", 0, 2, "related", System.currentTimeMillis());
        relatedUnit.getSlices().add(relatedSlice);
        memoryCapability.remember(relatedUnit);
        runtime.recordMemory(relatedUnit, "topic->related", List.of(), new ActivationProfile(1.0f, 1.0f, 1.0f));

        List<ActivatedMemorySlice> topicResult = runtime.queryActivatedMemoryByTopicPath("topic->main");
        assertEquals(List.of("slice-primary-zero"), topicResult.stream().map(ActivatedMemorySlice::getSliceId).toList());
    }

    @Test
    void shouldRefreshBindingTimestampAndActivationProfileWhenSameSliceRebound() throws Exception {
        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-test");
        MemoryRuntime runtime = new MemoryRuntime();
        setField(runtime, "memoryCapability", memoryCapability);
        setField(runtime, "cognitionCapability", stubCognitionCapability(List.of(message("seed"))));

        MemoryUnit unit = new MemoryUnit("unit-refresh");
        unit.getConversationMessages().addAll(List.of(message("m0"), message("m1")));
        MemorySlice slice = MemorySlice.restore("slice-refresh", 0, 2, "summary", 86_400_000L);
        unit.getSlices().add(slice);
        memoryCapability.remember(unit);

        runtime.recordMemory(unit, "topic->main", List.of("topic->related"), new ActivationProfile(0.2f, 0.1f, 0.2f));
        unit.getSlices().clear();
        unit.getSlices().add(MemorySlice.restore("slice-refresh", 0, 2, "summary", 172_800_000L));
        runtime.recordMemory(unit, "topic->main", List.of("topic->related-2"), new ActivationProfile(0.9f, 0.8f, 0.7f));

        JSONObject state = JSONObject.parseObject(runtime.convert().toString());
        JSONObject mainTopic = state.getJSONArray("topic_slices").stream()
                .map(JSONObject.class::cast)
                .filter(item -> "topic->main".equals(item.getString("topic_path")))
                .findFirst()
                .orElseThrow();
        JSONObject binding = mainTopic.getJSONArray("bindings").getJSONObject(0);
        JSONObject activationProfile = binding.getJSONObject("activation_profile");

        assertEquals(172_800_000L, binding.getLongValue("timestamp"));
        assertEquals(0.9f, activationProfile.getFloatValue("activation_weight"));
        assertEquals(0.8f, activationProfile.getFloatValue("diffusion_weight"));
        assertEquals(0.7f, activationProfile.getFloatValue("context_independence_weight"));
        assertEquals(
                List.of("topic->related", "topic->related-2"),
                binding.getJSONArray("related_topic_paths").toJavaList(String.class)
        );
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
