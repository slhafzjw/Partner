package work.slhaf.partner.core.memory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.framework.agent.model.pojo.Message;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryCoreTest {

    private static MemoryCore memoryCore;

    @BeforeAll
    static void beforeAll(@TempDir Path tempDir) {
        System.setProperty("user.home", tempDir.toAbsolutePath().toString());
        memoryCore = new MemoryCore();
    }

    @BeforeEach
    void setUp() {
        memoryCore.refreshMemorySession();
    }

    @Test
    void shouldCreateFirstSliceFromChatMessages() {
        String sessionId = memoryCore.getMemorySessionId();

        MemoryUnit updatedUnit = memoryCore.updateMemoryUnit(List.of(
                new Message(Message.Character.USER, "m0"),
                new Message(Message.Character.USER, "m1"),
                new Message(Message.Character.USER, "m2")
        ), "first-summary");

        assertEquals(sessionId, updatedUnit.getId());
        assertEquals(List.of("m0", "m1", "m2"),
                updatedUnit.getConversationMessages().stream().map(Message::getContent).toList());
        assertEquals(1, updatedUnit.getSlices().size());

        MemorySlice firstSlice = updatedUnit.getSlices().getFirst();
        assertNotNull(firstSlice.getId());
        assertEquals(0, firstSlice.getStartIndex());
        assertEquals(3, firstSlice.getEndIndex());
        assertEquals("first-summary", firstSlice.getSummary());
        assertTrue(updatedUnit.getTimestamp() > 0);
        assertTrue(firstSlice.getTimestamp() > 0);
    }

    @Test
    void shouldAppendMessagesAndCreateNextSlice() {
        String sessionId = memoryCore.getMemorySessionId();

        memoryCore.updateMemoryUnit(List.of(
                new Message(Message.Character.USER, "m0")
        ), "first-summary");

        MemoryUnit updatedUnit = memoryCore.updateMemoryUnit(List.of(
                new Message(Message.Character.ASSISTANT, "m1"),
                new Message(Message.Character.USER, "m2")
        ), "second-summary");

        assertEquals(sessionId, updatedUnit.getId());
        assertEquals(List.of("m0", "m1", "m2"),
                updatedUnit.getConversationMessages().stream().map(Message::getContent).toList());
        assertEquals(2, updatedUnit.getSlices().size());

        MemorySlice appendedSlice = updatedUnit.getSlices().getLast();
        assertNotNull(appendedSlice.getId());
        assertEquals(1, appendedSlice.getStartIndex());
        assertEquals(3, appendedSlice.getEndIndex());
        assertEquals("second-summary", appendedSlice.getSummary());
        assertTrue(appendedSlice.getTimestamp() > 0);

        MemorySlice loadedSlice = memoryCore.getMemorySlice(sessionId, appendedSlice.getId()).getOrThrow();
        assertNotNull(loadedSlice);
        assertEquals(1, loadedSlice.getStartIndex());
        assertEquals(3, loadedSlice.getEndIndex());
        assertEquals("second-summary", loadedSlice.getSummary());
    }
}
