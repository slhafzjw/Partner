package work.slhaf.partner.core.memory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import work.slhaf.partner.common.config.Config;
import work.slhaf.partner.common.config.PartnerAgentConfigLoader;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.framework.agent.config.AgentConfigLoader;
import work.slhaf.partner.framework.agent.model.pojo.Message;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryCoreTest {

    private AgentConfigLoader previousLoader;
    private String agentId;

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

    @AfterEach
    void tearDown() throws Exception {
        AgentConfigLoader.INSTANCE = previousLoader;
        if (agentId != null) {
            Files.deleteIfExists(Path.of("data/memory", agentId + "-memory-core.memory"));
            Files.deleteIfExists(Path.of("data/memory", agentId + "-temp-memory-core.memory"));
        }
    }

    @Test
    void shouldNormalizeSliceEndIndexUsingExclusiveUpperBound() throws Exception {
        agentId = "memory-core-test-" + UUID.randomUUID();
        previousLoader = AgentConfigLoader.INSTANCE;
        AgentConfigLoader.INSTANCE = testLoader(agentId);

        MemoryCore memoryCore = new MemoryCore();

        MemorySlice slice = new MemorySlice();
        slice.setId("slice-1");
        slice.setStartIndex(1);
        slice.setEndIndex(99);

        MemoryUnit unit = new MemoryUnit();
        unit.setId("unit-1");
        unit.setConversationMessages(new ArrayList<>(List.of(
                new Message(Message.Character.USER, "m0"),
                new Message(Message.Character.USER, "m1"),
                new Message(Message.Character.USER, "m2")
        )));
        unit.setSlices(new ArrayList<>(List.of(slice)));

        memoryCore.saveMemoryUnit(unit);

        MemorySlice savedSlice = memoryCore.getMemorySlice("unit-1", "slice-1");
        assertEquals(1, savedSlice.getStartIndex());
        assertEquals(3, savedSlice.getEndIndex());
    }
}
