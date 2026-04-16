package work.slhaf.partner.core.cognition;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import work.slhaf.partner.framework.agent.model.pojo.Message;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CognitionCoreTest {

    @BeforeAll
    static void beforeAll(@TempDir Path tempDir) {
        System.setProperty("user.home", tempDir.toAbsolutePath().toString());
    }

    @Test
    void shouldRenderRecentChatMessagesWithWrapperAndNotes() {
        CognitionCore cognitionCore = new CognitionCore();
        cognitionCore.getChatMessages().addAll(List.of(
                new Message(Message.Character.USER, "[[USER]: user-1]: hello"),
                new Message(Message.Character.ASSISTANT, "[NOT_REPLIED]: wait"),
                new Message(Message.Character.ASSISTANT, "latest message")
        ));

        cognitionCore.refreshRecentChatMessagesContext();
        String content = cognitionCore.contextWorkspace()
                .resolve(List.of(ContextBlock.FocusedDomain.COMMUNICATION))
                .encodeToMessage()
                .getContent();

        assertTrue(content.contains("<message_tag_notes>"));
        assertTrue(content.contains("<chat_messages>"));
        assertTrue(content.contains("<chat_message role=\"user\">[[USER]: user-1]: hello</chat_message>"));
        assertTrue(content.contains("<chat_message role=\"assistant\">[NOT_REPLIED]: wait</chat_message>"));
        assertTrue(content.contains("[USER]"));
        assertTrue(content.contains("[AGENT]"));
        assertTrue(content.contains("[NOT_REPLIED]"));
        assertFalse(content.contains("latest message"));
    }
}
