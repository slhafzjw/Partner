package work.slhaf.partner.module.communication;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.context.ContextWorkspace;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.runtime.PartnerRunningFlowContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

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

    private static String invokeBuildInputXml(
            CommunicationProducer producer,
            PartnerRunningFlowContext context,
            List<?> communicationBlocks
    ) throws Exception {
        Method method = CommunicationProducer.class.getDeclaredMethod(
                "buildInputXml",
                PartnerRunningFlowContext.class,
                List.class
        );
        method.setAccessible(true);
        return (String) method.invoke(producer, context, communicationBlocks);
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
        assertEquals("[[USER]: user-1]:\n\nhello", chatMessages.get(0).getContent());
        assertEquals("[[AGENT]: self]: [NOT_REPLIED]:\n\nnot now", chatMessages.get(1).getContent());
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
        assertEquals("[[AGENT]: self]:\n\nnormal reply", chatMessages.get(1).getContent());
    }

    @Test
    void shouldBuildInputXmlWithoutExtraWrapper() throws Exception {
        StubCognitionCapability cognitionCapability = new StubCognitionCapability();
        CommunicationProducer producer = new CommunicationProducer();
        setField(producer, "cognitionCapability", cognitionCapability);

        String xml = invokeBuildInputXml(
                producer,
                PartnerRunningFlowContext.fromUser("user-1", "hello"),
                List.of()
        );

        assertTrue(xml.contains("<input>"));
        assertTrue(xml.contains("<inputs>"));
        assertTrue(xml.contains("<input interval-to-first=\"0\">hello</input>"));
        assertFalse(xml.contains("<wrapper>"));
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
        public Element messageNotesElement() {
            return null;
        }

        @Override
        public Lock getMessageLock() {
            return lock;
        }

        @Override
        public java.util.Set<work.slhaf.partner.core.cognition.impression.ActiveEntity> projectEntity(String input) {
            return java.util.Set.of();
        }

        @Override
        public java.util.Map<
                work.slhaf.partner.core.cognition.impression.ActiveEntity,
                work.slhaf.partner.core.cognition.impression.Entity
                > showEntities() {
            return java.util.Map.of();
        }

        @Override
        public String createEntity(String subject) {
            return null;
        }

        @Override
        public work.slhaf.partner.core.cognition.impression.Entity getEntity(String uuid) {
            return null;
        }

        @Override
        public work.slhaf.partner.core.cognition.impression.ActiveEntity activateKnownEntity(String entityUuid) {
            return null;
        }

        @Override
        public boolean bindActiveEntity(String runtimeId, String entityUuid) {
            return false;
        }

        @Override
        public boolean renameEntitySubject(String entityUuid, String newSubject, boolean keepOldSubjectAsAlias) {
            return false;
        }

        @Override
        public boolean addEntityAlias(String entityUuid, String alias, boolean deprecated) {
            return false;
        }

        @Override
        public boolean updateEntityImpression(String entityUuid, String impression, String newImpression, double confidence) {
            return false;
        }

        @Override
        public boolean updateEntityFeature(String entityUuid, String feature, String newFeature, double confidence) {
            return false;
        }

        @Override
        public boolean updateEntityRelation(String entityUuid, String target, String relation, double strength) {
            return false;
        }
    }
}
