package work.slhaf.partner.module.memory.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import work.slhaf.partner.common.config.Config;
import work.slhaf.partner.common.config.PartnerAgentConfigLoader;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.core.memory.pojo.SliceRef;
import work.slhaf.partner.framework.agent.config.AgentConfigLoader;
import work.slhaf.partner.framework.agent.model.pojo.Message;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRuntimeTest {

    private AgentConfigLoader previousLoader;
    private String runtimeAgentId;

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

    private static PartnerAgentConfigLoader testLoader(String agentId) {
        PartnerAgentConfigLoader loader = new PartnerAgentConfigLoader();
        Config config = new Config();
        config.setAgentId(agentId);
        Config.WebSocketConfig webSocketConfig = new Config.WebSocketConfig();
        webSocketConfig.setPort(18080);
        config.setWebSocketConfig(webSocketConfig);
        loader.setConfig(config);
        loader.setModelConfigMap(new HashMap<>());
        return loader;
    }

    private static Message message(String content) {
        return new Message(Message.Character.USER, content);
    }

    @AfterEach
    void tearDown() throws Exception {
        AgentConfigLoader.INSTANCE = previousLoader;
        if (runtimeAgentId != null) {
            Files.deleteIfExists(Path.of("data/memory", runtimeAgentId + "-memory-runtime.memory"));
            Files.deleteIfExists(Path.of("data/memory", runtimeAgentId + "-memory-runtime-temp.memory"));
        }
    }

    @Test
    void shouldSliceMessagesUsingLeftClosedRightOpenRange() throws Exception {
        MemoryRuntime runtime = new MemoryRuntime();
        MemoryUnit unit = new MemoryUnit();
        unit.setConversationMessages(new ArrayList<>(List.of(
                message("m0"),
                message("m1"),
                message("m2"),
                message("m3")
        )));

        MemorySlice slice = new MemorySlice();
        slice.setStartIndex(1);
        slice.setEndIndex(3);

        List<Message> messages = invokeSliceMessages(runtime, unit, slice);
        assertEquals(List.of("m1", "m2"), messages.stream().map(Message::getContent).toList());

        slice.setStartIndex(2);
        slice.setEndIndex(2);
        assertTrue(invokeSliceMessages(runtime, unit, slice).isEmpty());
    }

    @Test
    void shouldBindTopicToLatestMemorySliceInsteadOfFirstSlice() throws Exception {
        runtimeAgentId = "runtime-test-" + UUID.randomUUID();
        previousLoader = AgentConfigLoader.INSTANCE;
        AgentConfigLoader.INSTANCE = testLoader(runtimeAgentId);

        StubMemoryCapability memoryCapability = new StubMemoryCapability("session-test");

        MemoryRuntime runtime = new MemoryRuntime();
        setField(runtime, "memoryCapability", memoryCapability);

        MemoryUnit unit = new MemoryUnit();
        unit.setId("unit-1");
        unit.setConversationMessages(new ArrayList<>(List.of(
                message("m0"),
                message("m1"),
                message("m2"),
                message("m3")
        )));

        MemorySlice firstSlice = new MemorySlice();
        firstSlice.setId("slice-1");
        firstSlice.setStartIndex(0);
        firstSlice.setEndIndex(2);
        firstSlice.setSummary("first");
        firstSlice.setTimestamp(1L);

        MemorySlice secondSlice = new MemorySlice();
        secondSlice.setId("slice-2");
        secondSlice.setStartIndex(2);
        secondSlice.setEndIndex(4);
        secondSlice.setSummary("second");
        secondSlice.setTimestamp(2L);

        unit.setSlices(new ArrayList<>(List.of(firstSlice, secondSlice)));

        runtime.recordMemory(unit, "topic/main", List.of("topic/related"));

        Map<String, CopyOnWriteArrayList<SliceRef>> topicSlices = topicSlices(runtime);
        assertEquals(List.of("slice-2"),
                topicSlices.get("topic/main").stream().map(SliceRef::getSliceId).toList());
        assertEquals(List.of("slice-2"),
                topicSlices.get("topic/related").stream().map(SliceRef::getSliceId).toList());
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
