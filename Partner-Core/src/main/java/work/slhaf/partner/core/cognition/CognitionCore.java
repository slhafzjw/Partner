package work.slhaf.partner.core.cognition;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import kotlin.Unit;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.common.base.Block;
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.framework.agent.interaction.AgentRuntime;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.state.State;
import work.slhaf.partner.framework.agent.state.StateSerializable;
import work.slhaf.partner.framework.agent.state.StateValue;
import work.slhaf.partner.runtime.PartnerRunningFlowContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@CapabilityCore(value = "cognition")
public class CognitionCore implements StateSerializable {

    private static final String RECENT_CHAT_MESSAGE_NOTES = """
            消息格式:
            - 所有消息统一写为“标记行 + 空行 + 正文”，比如：
            
                [[AGENT]: self]: [NOT_REPLIED][COMPRESSED]:
            
                正文内容
            
            - 标记行一定包含身份标签，通常格式为 [[USER]: <userName>] 或 [[AGENT]: self]
            - 若身份标签提取失败，可能回退为 [[Unknown]: Unknown]
            - 若存在其他标签，则写为“身份标签: 状态标签串:”
            - 正文永远从空行后开始
            
            标记含义:
            - [USER]: 外部用户来源
            - [AGENT]: 系统内部来源
            - [NOT_REPLIED]: 仅保留在历史中的未直接回复结果
            - [COMPRESSED]: 该消息正文经过压缩
            """;

    private final ReentrantLock messageLock = new ReentrantLock();

    /**
     * 主模型的聊天记录
     */
    private List<Message> chatMessages = new ArrayList<>();

    private final ContextWorkspace contextWorkspace = new ContextWorkspace();

    public CognitionCore() {
        register();
    }

    @CapabilityMethod
    public ContextWorkspace contextWorkspace() {
        return contextWorkspace;
    }

    @CapabilityMethod
    public void initiateTurn(String input, String target, String... skippedModules) {
        PartnerRunningFlowContext primaryContext = PartnerRunningFlowContext.fromSelf(input);
        primaryContext.setTarget(target);
        if (skippedModules != null) {
            for (String skippedModule : skippedModules) {
                primaryContext.addSkippedModule(skippedModule);
            }
        }
        AgentRuntime.INSTANCE.submit(primaryContext);
    }

    @CapabilityMethod
    public List<Message> getChatMessages() {
        return chatMessages;
    }

    @CapabilityMethod
    public List<Message> snapshotChatMessages() {
        messageLock.lock();
        try {
            return List.copyOf(chatMessages);
        } finally {
            messageLock.unlock();
        }
    }

    @CapabilityMethod
    public void rollChatMessagesWithSnapshot(int snapshotSize, int retainDivisor) {
        messageLock.lock();
        try {
            int safeSnapshotSize = Math.clamp(snapshotSize, 0, chatMessages.size());
            if (safeSnapshotSize == 0) {
                return;
            }
            int safeDivisor = Math.max(retainDivisor, 1);
            int retainCount = safeSnapshotSize / safeDivisor;
            int retainStart = Math.max(0, safeSnapshotSize - retainCount);

            List<Message> rolled = new ArrayList<>(chatMessages.subList(retainStart, safeSnapshotSize));
            if (chatMessages.size() > safeSnapshotSize) {
                rolled.addAll(chatMessages.subList(safeSnapshotSize, chatMessages.size()));
            }
            chatMessages = rolled;
        } finally {
            messageLock.unlock();
        }
    }

    @CapabilityMethod
    public Lock getMessageLock() {
        return messageLock;
    }

    @CapabilityMethod
    public void refreshRecentChatMessagesContext() {
        ContextBlock block = new ContextBlock(
                new BlockContent("recent_chat_messages", "communication_producer") {
                    @Override
                    protected void fillXml(@NotNull Document document, @NotNull Element root) {
                        root.appendChild(document.importNode(messageNotesElement(), true));
                        Element chatMessagesElement = document.createElement("chat_messages");
                        root.appendChild(chatMessagesElement);
                        appendRepeatedElements(document, chatMessagesElement, "chat_message", resolveRecentChatMessages(), (messageElement, message) -> {
                            messageElement.setAttribute("role", message.roleValue());
                            messageElement.setTextContent(message.getContent());
                            return Unit.INSTANCE;
                        });
                    }
                },
                Set.of(ContextBlock.FocusedDomain.COMMUNICATION),
                100,
                10,
                4
        );
        contextWorkspace.register(block);
    }

    @CapabilityMethod
    public Element messageNotesElement() {
        return new Block("message_tag_notes") {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                root.setTextContent(RECENT_CHAT_MESSAGE_NOTES);
            }
        }.encodeToXml();
    }

    private List<Message> resolveRecentChatMessages() {
        int exclusiveEnd = Math.max(chatMessages.size() - 1, 0);
        if (exclusiveEnd == 0) {
            return List.of();
        }
        int start = Math.max(exclusiveEnd - 6, 0);
        return chatMessages.subList(start, exclusiveEnd);
    }

    @Override
    public @NotNull Path statePath() {
        return Path.of("core", "cognition.json");
    }

    @Override
    public void load(@NotNull JSONObject state) {
        JSONArray messageArray = state.getJSONArray("chat_messages");

        if (messageArray == null) {
            log.warn("chat_messages is missing");
            return;
        }

        for (int i = 0; i < messageArray.size(); i++) {
            JSONObject messageObject = messageArray.getJSONObject(i);
            if (messageObject == null) {
                continue;
            }

            String role = messageObject.getString("role");
            String content = messageObject.getString("content");
            if (role == null || content == null) {
                continue;
            }

            this.chatMessages.add(new Message(Message.Character.fromValue(role), content));
        }

        refreshRecentChatMessagesContext();
    }

    @Override
    public @NotNull State convert() {
        State state = new State();

        List<StateValue.Obj> convertedMessageList = chatMessages.stream().map(message -> {
            Map<String, StateValue> convertedMap = Map.of(
                    "role", StateValue.str(message.roleValue()),
                    "content", StateValue.str(message.getContent())
            );
            return StateValue.obj(convertedMap);
        }).toList();
        state.append("chat_messages", StateValue.arr(convertedMessageList));

        return state;
    }
}
