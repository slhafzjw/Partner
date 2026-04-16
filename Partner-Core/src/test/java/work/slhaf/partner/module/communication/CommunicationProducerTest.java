package work.slhaf.partner.module.communication;

import org.junit.jupiter.api.Test;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextWorkspace;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.runtime.PartnerRunningFlowContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommunicationProducerTest {

    private static void invokeUpdateChatMessages(
            CommunicationProducer producer,
            PartnerRunningFlowContext context,
            String response
    ) throws Exception {
        Method method = CommunicationProducer.class.getDeclaredMethod(
                "updateChatMessages",
                PartnerRunningFlowContext.class,
                String.class
        );
        method.setAccessible(true);
        method.invoke(producer, context, response);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void shouldConvertNoReplyResponseWhenWritingHistory() throws Exception {
        StubCognitionCapability cognitionCapability = new StubCognitionCapability();
        CommunicationProducer producer = new CommunicationProducer();
        setField(producer, "cognitionCapability", cognitionCapability);

        invokeUpdateChatMessages(
                producer,
                PartnerRunningFlowContext.fromUser("user-1", "hello"),
                "NO_REPLY\nnot now"
        );

        List<Message> chatMessages = cognitionCapability.getChatMessages();
        assertEquals(2, chatMessages.size());
        assertEquals("[[USER]: user-1]: hello", chatMessages.get(0).getContent());
        assertEquals("[NOT_REPLIED]: not now", chatMessages.get(1).getContent());
    }

    @Test
    void shouldKeepRegularAssistantResponseUntouched() throws Exception {
        StubCognitionCapability cognitionCapability = new StubCognitionCapability();
        CommunicationProducer producer = new CommunicationProducer();
        setField(producer, "cognitionCapability", cognitionCapability);

        invokeUpdateChatMessages(
                producer,
                PartnerRunningFlowContext.fromUser("user-1", "hello"),
                "normal reply"
        );

        List<Message> chatMessages = cognitionCapability.getChatMessages();
        assertEquals("normal reply", chatMessages.get(1).getContent());
    }

    private static final class StubCognitionCapability implements CognitionCapability {
        private final ContextWorkspace contextWorkspace = new ContextWorkspace();
        private final List<Message> chatMessages = new ArrayList<>();
        private final Lock lock = new ReentrantLock();

        @Override
        public void initiateTurn(String input, String target, String... skippedModules) {
        }

        @Override
        public ContextWorkspace contextWorkspace() {
            return contextWorkspace;
        }

        @Override
        public List<Message> getChatMessages() {
            return chatMessages;
        }

        @Override
        public List<Message> snapshotChatMessages() {
            return List.copyOf(chatMessages);
        }

        @Override
        public void rollChatMessagesWithSnapshot(int snapshotSize, int retainDivisor) {
        }

        @Override
        public void refreshRecentChatMessagesContext() {
        }

        @Override
        public Lock getMessageLock() {
            return lock;
        }
    }
}
