package work.slhaf.partner.core.cognition;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
                        appendRepeatedElements(document, root, "chat_message", resolveRecentChatMessages());
                    }
                },
                Set.of(ContextBlock.FocusedDomain.COMMUNICATION),
                100,
                10,
                4
        );
        contextWorkspace.register(block);
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
