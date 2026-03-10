package work.slhaf.partner.module.modules.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jetbrains.annotations.NotNull;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.api.agent.runtime.interaction.flow.ContextBlock;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.api.chat.pojo.MetaMessage;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommunicationProducerTest {

    @Mock
    private CognationCapability cognationCapability;

    private TestCommunicationProducer producer;

    @BeforeEach
    void setUp() {
        producer = new TestCommunicationProducer();
        producer.setCognationCapability(cognationCapability);
        lenient().when(cognationCapability.getMessageLock()).thenReturn(new ReentrantLock());
    }

    @Test
    void execute_shouldAssembleHeadContextConversationAndPersistCompactHistory() {
        List<Message> history = List.of(
                new Message(Message.Character.USER, "[USER]: old-user: 旧消息"),
                new Message(Message.Character.ASSISTANT, "旧回复")
        );
        lenient().when(cognationCapability.getChatMessages()).thenReturn(history);

        producer.init();

        PartnerRunningFlowContext context = PartnerRunningFlowContext.Companion.fromUser(
                "u-1",
                "你好，介绍一下你现在看到的上下文",
                "wechat",
                "tester"
        );
        context.getContextBlocks().add(new TestContextBlock(20, "memory", "slice-A"));
        context.getContextBlocks().add(new TestContextBlock(10, "perceive", "slice-B"));
        context.getContextBlocks().add(new TestContextBlock(30, ContextBlock.Type.SUPPLY, "tool", "source-a", "supply-A"));
        context.getContextBlocks().add(new TestContextBlock(40, ContextBlock.Type.SUPPLY, "tool", "source-b", "supply-B"));

        producer.execute(context);

        List<Message> sentMessages = producer.getCapturedMessages();
        assertEquals(5, sentMessages.size());
        assertEquals(Message.Character.SYSTEM, sentMessages.get(0).getRole());
        assertTrue(sentMessages.get(0).getContent().contains("Head"));

        String contextXml = sentMessages.get(1).getContent();
        assertTrue(contextXml.contains("<context>"));
        assertTrue(contextXml.indexOf("slice-B") < contextXml.indexOf("slice-A"));
        assertTrue(contextXml.contains("source=\"perceive\""));
        assertTrue(contextXml.contains("source=\"memory\""));
        assertFalse(contextXml.contains("supply-A"));
        assertFalse(contextXml.contains("supply-B"));

        String inputXml = sentMessages.get(4).getContent();
        assertTrue(inputXml.contains("<input>"));
        assertTrue(inputXml.contains("<content>你好，介绍一下你现在看到的上下文</content>"));
        assertTrue(inputXml.contains("<source>[USER]: u-1</source>"));
        assertTrue(inputXml.contains("<platform>wechat</platform>"));
        assertTrue(inputXml.contains("<nickname>tester</nickname>"));
        assertTrue(inputXml.contains("<tool>"));
        assertEquals(inputXml.indexOf("<tool>"), inputXml.lastIndexOf("<tool>"));
        assertTrue(inputXml.contains("source=\"source-a\""));
        assertTrue(inputXml.contains("source=\"source-b\""));
        assertTrue(inputXml.contains("supply-A"));
        assertTrue(inputXml.contains("supply-B"));

        assertEquals(4, producer.getChatMessages().size());
        Message lastUserMessage = producer.getChatMessages().get(2);
        assertEquals(Message.Character.USER, lastUserMessage.getRole());
        assertTrue(lastUserMessage.getContent().startsWith("[USER]: u-1: 你好，介绍一下你现在看到的上下文"));
        assertFalse(lastUserMessage.getContent().contains("<input>"));

        Message lastAssistantMessage = producer.getChatMessages().get(3);
        assertEquals("收到", lastAssistantMessage.getContent());

        ArgumentCaptor<MetaMessage> metaMessageCaptor = ArgumentCaptor.forClass(MetaMessage.class);
        verify(cognationCapability).addMetaMessage(anyString(), metaMessageCaptor.capture());
        MetaMessage metaMessage = metaMessageCaptor.getValue();
        assertNotNull(metaMessage);
        assertTrue(metaMessage.getUserMessage().getContent().startsWith("[USER]: u-1: 你好，介绍一下你现在看到的上下文"));
        assertEquals("收到", metaMessage.getAssistantMessage().getContent());
        assertEquals("收到", context.getCoreResponse().getString("text"));
    }

    @Test
    void execute_shouldDropLegacyStructuredUserMessagesFromHistory() {
        List<Message> history = new ArrayList<>();
        history.add(new Message(Message.Character.USER, "<input><content>legacy</content></input>"));
        history.add(new Message(Message.Character.ASSISTANT, "legacy-assistant"));
        history.add(new Message(Message.Character.USER, "{\"text\":\"legacy-json\"}"));
        lenient().when(cognationCapability.getChatMessages()).thenReturn(history);

        producer.init();

        PartnerRunningFlowContext context = PartnerRunningFlowContext.Companion.fromSelf("新输入");
        producer.execute(context);

        List<Message> updatedHistory = producer.getChatMessages();
        assertEquals(3, updatedHistory.size());
        assertEquals("legacy-assistant", updatedHistory.get(0).getContent());
        assertTrue(updatedHistory.get(1).getContent().startsWith("[AGENT]: self: 新输入"));
        assertEquals("收到", updatedHistory.get(2).getContent());
    }

    @Test
    void execute_shouldSkipContextMessageWhenOnlySupplyBlocksExist() {
        lenient().when(cognationCapability.getChatMessages()).thenReturn(List.of());

        producer.init();

        PartnerRunningFlowContext context = PartnerRunningFlowContext.Companion.fromUser(
                "u-2",
                "只有补充块",
                "qq",
                "tester2"
        );
        context.getContextBlocks().add(new TestContextBlock(5, ContextBlock.Type.SUPPLY, "tool", "source-x", "supply-X"));

        producer.execute(context);

        List<Message> sentMessages = producer.getCapturedMessages();
        assertEquals(2, sentMessages.size());
        assertEquals(Message.Character.SYSTEM, sentMessages.get(0).getRole());
        assertTrue(sentMessages.get(1).getContent().contains("<input>"));
        assertFalse(sentMessages.get(1).getContent().contains("<context>"));
        assertTrue(sentMessages.get(1).getContent().contains("<tool>"));
        assertTrue(sentMessages.get(1).getContent().contains("supply-X"));
    }

    private static final class TestCommunicationProducer extends CommunicationProducer {
        private List<Message> capturedMessages = List.of();

        @Override
        public @NotNull String chat(@NotNull List<Message> messages) {
            capturedMessages = new ArrayList<>(mergeMessages(messages));
            return "收到";
        }

        List<Message> getCapturedMessages() {
            return capturedMessages;
        }
    }

    private static final class TestContextBlock extends ContextBlock {
        private final int priority;
        private final Type type;
        private final String blockName;
        private final String source;
        private final String payload;

        private TestContextBlock(int priority, String source, String payload) {
            this(priority, Type.CONTEXT, "test-block", source, payload);
        }

        private TestContextBlock(int priority, Type type, String blockName, String source, String payload) {
            this.priority = priority;
            this.type = type;
            this.blockName = blockName;
            this.source = source;
            this.payload = payload;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public @NotNull Type getType() {
            return type;
        }

        @Override
        public @NotNull String getBlockName() {
            return blockName;
        }

        @Override
        public @NotNull String getSource() {
            return source;
        }

        @Override
        protected void fillXml(@NotNull Document document, @NotNull Element root) {
            appendTextElement(document, root, "payload", payload);
        }
    }
}
