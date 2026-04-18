package work.slhaf.partner.module.communication;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.cognition.*;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.StreamChatMessageConsumer;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.runtime.PartnerRunningFlowContext;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class CommunicationProducer extends AbstractAgentModule.Running<PartnerRunningFlowContext> implements ActivateModel {

    private static final String INTERRUPTED_MARKER = " [response interrupted due to internal exception]";
    private static final String NO_REPLY_MARKER = "NO_REPLY";
    private static final String AGENT_MARKER = "[[AGENT]: self]";
    private static final String NOT_REPLIED_PREFIX = "[NOT_REPLIED]";
    private static final String MARKER_BODY_SEPARATOR = ":\n\n";

    private static final String MODULE_PROMPT = """
            你当前正在承担 Partner 的对外交流职责。你需要基于系统此刻的上下文状态、保留的对话轨迹以及最新输入，生成自然、贴合当前情境、并与系统整体状态一致的交流结果。
            
            你接下来收到的消息，将按照出现顺序，固定分为三个区段：
            1. system message 是 Head，用于说明整个输入结构与输出要求，即本条消息。
            2. <context> 区段承载系统中所有模块产生的上下文块。它表示 Partner 在当前时刻的系统状态投影，不应被理解为普通聊天历史；其中每个子块都带有独立来源，可作为理解当前状态和辅助决策的依据。
            3. <conversation> 区段是系统此刻保留的对话轨迹，用于帮助你理解当前交流延续、最近问答关系与最新输入所处的位置；最新的一条 user message 会使用 <input> 结构，其中 <inputs> 承载本轮按时间顺序排列的输入序列，每个 <input> 节点会带有相对首条输入的时间间隔属性；其他子标签是输入元信息与 type=SUPPLY 的补充块，补充块会按 blockName 分区。
            
            你的任务：
            - 最新输入是当前交流的直接触发点。
            - <conversation> 主要用于理解对话延续关系。
            - <context> 主要用于理解 Partner 此刻的系统状态；其中明显相关的状态信号不应被当作普通历史材料忽略。
            - 若最新输入与已有上下文存在张力，应以最新输入为当前交流的直接依据，再结合 <conversation> 与 <context> 判断如何回应。
            - 你当前负责的是对外交流，不负责直接规划行动、修改系统状态，或伪造并不存在的执行结果。
            
            输出契约：
            - 默认情况下，直接输出要发送给用户的最终回复正文，不要添加额外标签、解释或前后缀。
            - 若当前情境下不应直接向用户发出回复，但仍需要留下本轮交流结果供系统后续保留在交流轨迹中，则输出以 NO_REPLY 开头。
            - 使用 NO_REPLY 时，格式为：
            
            NO_REPLY
            这里写本轮交流结果正文
            
            - 以 NO_REPLY 开头的输出不会直接展示给用户；系统在写入交流轨迹时，会以单独的历史标记形式保留该结果。
            - 不要输出空字符串；若选择不直接回复用户，应使用 NO_REPLY 契约明确表达。
            """;

    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Init
    public void init() {
        log.info("CommunicationProducer 注册完毕...");
    }

    @Override
    public @NotNull String modelKey() {
        return "communication_producer";
    }

    @Override
    public @NotNull List<Message> modulePrompt() {
        return List.of(new Message(Message.Character.SYSTEM, MODULE_PROMPT));
    }

    @Override
    protected void doExecute(PartnerRunningFlowContext runningFlowContext) {
        log.debug("Communicating with: {}", runningFlowContext.getSource());
        executeChat(runningFlowContext);
    }

    private void executeChat(PartnerRunningFlowContext runningFlowContext) {
        StreamChatMessageConsumer consumer = ReplyDispatcher.INSTANCE.createConsumer(runningFlowContext.getTarget());
        this.streamChat(buildChatMessages(runningFlowContext), consumer)
                .onFailure(exception -> consumer.onDelta(INTERRUPTED_MARKER));
        updateChatMessages(runningFlowContext, consumer.collectResponse());
        cognitionCapability.refreshRecentChatMessagesContext();
    }

    private List<Message> buildChatMessages(PartnerRunningFlowContext runningFlowContext) {
        ResolvedContext resolvedContext = cognitionCapability.contextWorkspace()
                .resolve(List.of(ContextBlock.FocusedDomain.COGNITION, ContextBlock.FocusedDomain.ACTION, ContextBlock.FocusedDomain.MEMORY, ContextBlock.FocusedDomain.PERCEIVE));
        List<BlockContent> communicationBlocks = resolvedContext.getBlocks();
        List<Message> historyMessages = snapshotConversationMessages();
        List<Message> temp = new ArrayList<>(historyMessages.size() + 2);
        temp.add(buildContextMessage(communicationBlocks));
        temp.addAll(historyMessages);
        temp.add(buildInputMessage(runningFlowContext, communicationBlocks));
        return temp;
    }

    private void updateChatMessages(PartnerRunningFlowContext runningFlowContext, String response) {
        cognitionCapability.getMessageLock().lock();
        try {
            List<Message> chatMessages = cognitionCapability.getChatMessages();
            chatMessages.removeIf(this::isStructuredUserMessage);
            Message primaryUserMessage = new Message(
                    Message.Character.USER,
                    formatConversationUserMessage(runningFlowContext)
            );
            chatMessages.add(primaryUserMessage);
            Message assistantMessage = new Message(
                    Message.Character.ASSISTANT,
                    normalizeAssistantHistoryMessage(response)
            );
            chatMessages.add(assistantMessage);
        } finally {
            cognitionCapability.getMessageLock().unlock();
        }
    }

    private String normalizeAssistantHistoryMessage(String response) {
        String trimmed = response == null ? "" : response.trim();
        if (trimmed.equals(NO_REPLY_MARKER)) {
            return formatMarkedHistoryMessage(AGENT_MARKER, NOT_REPLIED_PREFIX, "");
        }
        if (trimmed.startsWith(NO_REPLY_MARKER + "\n")) {
            return formatMarkedHistoryMessage(
                    AGENT_MARKER,
                    NOT_REPLIED_PREFIX,
                    trimmed.substring((NO_REPLY_MARKER + "\n").length()).trim()
            );
        }
        if (trimmed.startsWith(NO_REPLY_MARKER + "\r\n")) {
            return formatMarkedHistoryMessage(
                    AGENT_MARKER,
                    NOT_REPLIED_PREFIX,
                    trimmed.substring((NO_REPLY_MARKER + "\r\n").length()).trim()
            );
        }
        return formatMarkedHistoryMessage(AGENT_MARKER, "", trimmed);
    }

    private List<Message> snapshotConversationMessages() {
        List<Message> snapshot = new ArrayList<>(cognitionCapability.snapshotChatMessages());
        snapshot.removeIf(this::isStructuredUserMessage);
        return snapshot;
    }

    private Message buildContextMessage(List<BlockContent> communicationBlocks) {
        List<BlockContent> contextBlocks = communicationBlocks.stream()
                .filter(this::belongsToContextSection)
                .toList();
        return new ResolvedContext(contextBlocks).encodeToMessage();
    }

    private Message buildInputMessage(PartnerRunningFlowContext runningFlowContext, List<BlockContent> communicationBlocks) {
        return new Message(Message.Character.USER, buildInputXml(runningFlowContext, communicationBlocks));
    }

    private String buildInputXml(PartnerRunningFlowContext runningFlowContext, List<BlockContent> communicationBlocks) {
        try {
            Document document = newDocument();
            Element root = document.createElement("input");
            document.appendChild(root);

            document.appendChild(document.importNode(runningFlowContext.encodeInputsBlock().encodeToXml(), true));
            appendTextElement(document, root, "source", runningFlowContext.getSource());
            for (Map.Entry<String, String> entry : runningFlowContext.getAdditionalUserInfo().entrySet()) {
                appendTextElement(document, root, sanitizeTagName(entry.getKey()), entry.getValue());
            }
            appendSupplyBlocks(document, root, communicationBlocks);

            return toXmlString(document);
        } catch (Exception e) {
            throw new IllegalStateException("构建 input 区段失败", e);
        }
    }

    private boolean isStructuredUserMessage(Message message) {
        if (message.getRole() == Message.Character.ASSISTANT) {
            return false;
        }
        String content = message.getContent();
        String trimmed = content.trim();
        return trimmed.startsWith("<input>") || trimmed.startsWith("<context>") || trimmed.startsWith("<?xml");
    }

    private boolean belongsToContextSection(BlockContent blockContent) {
        if (!(blockContent instanceof CommunicationBlockContent communicationBlockContent)) {
            return true;
        }
        return communicationBlockContent.getType() == CommunicationBlockContent.Projection.CONTEXT;
    }

    private String formatConversationUserMessage(PartnerRunningFlowContext runningFlowContext) {
        return formatMarkedHistoryMessage("[" + runningFlowContext.getSource() + "]", "", runningFlowContext.formatInputsForHistory());
    }

    private String formatMarkedHistoryMessage(String identityMarker, String statusMarkers, String body) {
        String markerLine = statusMarkers == null || statusMarkers.isBlank()
                ? identityMarker
                : identityMarker + ": " + statusMarkers;
        if (body == null || body.isBlank()) {
            return markerLine + ":";
        }
        return markerLine + MARKER_BODY_SEPARATOR + body.trim();
    }

    private Document newDocument() throws Exception {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .newDocument();
    }

    private void appendTextElement(Document document, Element parent, String tagName, String value) {
        Element element = document.createElement(tagName);
        element.setTextContent(value == null ? "" : value);
        parent.appendChild(element);
    }

    private void appendSupplyBlocks(Document document, Element inputRoot, List<BlockContent> contextBlocks) {
        Map<String, List<CommunicationBlockContent>> groupedBlocks = contextBlocks.stream()
                .filter(CommunicationBlockContent.class::isInstance)
                .map(CommunicationBlockContent.class::cast)
                .filter(block -> block.getType() == CommunicationBlockContent.Projection.SUPPLY)
                .collect(Collectors.groupingBy(
                        block -> sanitizeTagName(block.getBlockName()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (Map.Entry<String, List<CommunicationBlockContent>> entry : groupedBlocks.entrySet()) {
            Element groupElement = document.createElement(entry.getKey());
            inputRoot.appendChild(groupElement);
            for (CommunicationBlockContent block : entry.getValue()) {
                Element blockElement = block.encodeToXml();
                groupElement.appendChild(document.importNode(blockElement, true));
            }
        }
    }

    private String sanitizeTagName(String rawTagName) {
        if (rawTagName == null || rawTagName.isBlank()) {
            return "meta";
        }
        String sanitized = rawTagName.replaceAll("[^A-Za-z0-9_.-]", "_");
        if (!Character.isLetter(sanitized.charAt(0)) && sanitized.charAt(0) != '_') {
            sanitized = "_" + sanitized;
        }
        return sanitized;
    }

    private String toXmlString(Document document) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    @Override
    public int order() {
        return 5;
    }
}
