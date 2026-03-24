package work.slhaf.partner.module.modules.core;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.factory.component.annotation.Init;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.cognition.*;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

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

import static work.slhaf.partner.common.util.ExtractUtil.extractJson;

@EqualsAndHashCode(callSuper = true)
@Data
public class CommunicationProducer extends AbstractAgentModule.Running<PartnerRunningFlowContext> implements ActivateModel {

    private static final String MODULE_PROMPT = """
            你是 Partner 的表达模块。
            你接下来收到的消息固定分为三个区段:
            1. system message 是 Head, 用于说明整个输入结构与输出要求。
            2. <context> 区段只承载 type=CONTEXT 的上下文块, 其中每个子块都带有独立来源, 仅作为理解当前状态与辅助决策的依据。
            3. Conversation 区段是对话轨迹; 最新的一条 user message 会使用 <input> 结构, 其中 <content> 是本轮用户原始输入, 其他子标签是输入元信息与 type=SUPPLY 的补充块, 补充块会按 blockName 分区。
            你必须综合 Context 与 Conversation 回答最新输入, 不要把 XML 标签当作需要原样复述给用户的内容。
            直接输出最终回应内容即可, 不需要额外包装为 JSON。
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
    public boolean useStreaming() {
        return true;
    }

    @Override
    public @NotNull List<Message> modulePrompt() {
        return List.of(new Message(Message.Character.SYSTEM, MODULE_PROMPT));
    }

    @Override
    public void execute(PartnerRunningFlowContext runningFlowContext) {
        log.debug("Communicating with: {}", runningFlowContext.getSource());
        executeChat(runningFlowContext);
    }

    private void executeChat(PartnerRunningFlowContext runningFlowContext) {
        String responseText = null;

        // TODO considering removing retries in module
        int count = 0;
        while (true) {
            try {
                // TODO 为各模块提供 emit msg 能力后, 在这里统一接收并分发结构化输出.
                responseText = this.chat(buildChatMessages(runningFlowContext));
                log.debug("CommunicationProducer responses: {}", responseText);
                updateModuleContextAndChatMessages(runningFlowContext, responseText);
                break;
            } catch (Exception e) {
                count++;
                log.error("Communicating exception occurred: {}", e.getLocalizedMessage());
                if (count > 3) {
                    responseText = "CommunicationProducer Failed: " + e.getLocalizedMessage();
                    break;
                }
            } finally {
                updateCoreResponse(runningFlowContext, responseText);
            }
        }
    }

    private void updateCoreResponse(PartnerRunningFlowContext runningFlowContext, String responseText) {
        runningFlowContext.getCoreResponse().put("text", responseText);
    }

    private List<Message> buildChatMessages(PartnerRunningFlowContext runningFlowContext) {
        ResolvedContext resolvedContext = cognitionCapability.contextWorkspace()
                .resolve(List.of(ContextBlock.VisibleDomain.COMMUNICATION));
        List<BlockContent> communicationBlocks = resolvedContext.getBlocks();
        List<Message> historyMessages = snapshotConversationMessages();
        List<Message> temp = new ArrayList<>(historyMessages.size() + 2);
        temp.add(buildContextMessage(communicationBlocks));
        temp.addAll(historyMessages);
        temp.add(buildInputMessage(runningFlowContext, communicationBlocks));
        return temp;
    }

    private void updateModuleContextAndChatMessages(PartnerRunningFlowContext runningFlowContext, String response) {
        cognitionCapability.getMessageLock().lock();
        try {
            List<Message> chatMessages = cognitionCapability.getChatMessages();
            chatMessages.removeIf(this::isStructuredUserMessage);
            Message primaryUserMessage = new Message(
                    Message.Character.USER,
                    formatConversationUserMessage(runningFlowContext)
            );
            chatMessages.add(primaryUserMessage);
            Message assistantMessage = new Message(Message.Character.ASSISTANT, response);
            chatMessages.add(assistantMessage);
        } finally {
            cognitionCapability.getMessageLock().unlock();
        }
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
        return new ResolvedContext(contextBlocks).encodeToContextMessage();
    }

    private Message buildInputMessage(PartnerRunningFlowContext runningFlowContext, List<BlockContent> communicationBlocks) {
        return new Message(Message.Character.USER, buildInputXml(runningFlowContext, communicationBlocks));
    }

    private String buildInputXml(PartnerRunningFlowContext runningFlowContext, List<BlockContent> communicationBlocks) {
        try {
            Document document = newDocument();
            Element root = document.createElement("input");
            document.appendChild(root);

            appendTextElement(document, root, "content", runningFlowContext.getInput());
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
        if (trimmed.startsWith("<input>") || trimmed.startsWith("<context>") || trimmed.startsWith("<?xml")) {
            return true;
        }
        try {
            JSONObject.parseObject(extractJson(content));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean belongsToContextSection(BlockContent blockContent) {
        if (!(blockContent instanceof CommunicationBlockContent communicationBlockContent)) {
            return true;
        }
        return communicationBlockContent.getType() == CommunicationBlockContent.Projection.CONTEXT;
    }

    private String formatConversationUserMessage(PartnerRunningFlowContext runningFlowContext) {
        return "[" + runningFlowContext.getSource() + "]" + ": " + runningFlowContext.getInput();
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
