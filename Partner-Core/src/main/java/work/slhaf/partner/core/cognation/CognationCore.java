package work.slhaf.partner.core.cognation;

import com.alibaba.fastjson2.JSONObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.api.agent.runtime.interaction.AgentRuntime;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.api.chat.pojo.MetaMessage;
import work.slhaf.partner.core.PartnerCore;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.io.IOException;
import java.io.Serial;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@CapabilityCore(value = "cognation")
@Getter
@Setter
public class CognationCore extends PartnerCore<CognationCore> {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ReentrantLock messageLock = new ReentrantLock();

    /**
     * 主模型的聊天记录
     */
    private List<Message> chatMessages = new ArrayList<>();
    private HashMap<String /*startUserId*/, List<MetaMessage>> singleMetaMessageMap = new HashMap<>();
    private String currentMemoryId;
    private long lastUpdatedTime;

    public CognationCore() throws IOException, ClassNotFoundException {
    }

    @CapabilityMethod
    public String initiateTurn(String input) {
        PartnerRunningFlowContext primaryContext = PartnerRunningFlowContext.Companion.fromSelf(input);
        PartnerRunningFlowContext executedContext = AgentRuntime.INSTANCE.submit(primaryContext);
        return executedContext.getCoreResponse().getString("text");
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
            int safeSnapshotSize = Math.max(0, Math.min(snapshotSize, chatMessages.size()));
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
    public long getLastUpdatedTime() {
        return lastUpdatedTime;
    }

    @CapabilityMethod
    public HashMap<String, List<MetaMessage>> getSingleMetaMessageMap() {
        return singleMetaMessageMap;
    }

    @CapabilityMethod
    public String getCurrentMemoryId() {
        return currentMemoryId;
    }

    @CapabilityMethod
    public void cleanMessage(List<Message> messages) {
        messageLock.lock();
        try {
            this.getChatMessages().removeAll(messages);
        } finally {
            messageLock.unlock();
        }
    }

    @CapabilityMethod
    public Lock getMessageLock() {
        return messageLock;
    }

    @CapabilityMethod
    public void addMetaMessage(String userId, MetaMessage metaMessage) {
        log.debug("[{}] 当前会话历史: {}", getCoreKey(), JSONObject.toJSONString(singleMetaMessageMap));
        messageLock.lock();
        try {
            if (singleMetaMessageMap.containsKey(userId)) {
                singleMetaMessageMap.get(userId).add(metaMessage);
            } else {
                singleMetaMessageMap.put(userId, new java.util.ArrayList<>());
                singleMetaMessageMap.get(userId).add(metaMessage);
            }
        } finally {
            messageLock.unlock();
        }
        log.debug("[{}] 会话历史更新: {}", getCoreKey(), JSONObject.toJSONString(singleMetaMessageMap));
    }

    @CapabilityMethod
    public List<Message> unpackAndClear(String userId) {
        messageLock.lock();
        try {
            List<Message> messages = new ArrayList<>();
            List<MetaMessage> metaMessages = singleMetaMessageMap.get(userId);
            if (metaMessages == null) {
                return messages;
            }
            for (MetaMessage metaMessage : metaMessages) {
                messages.add(metaMessage.getUserMessage());
                messages.add(metaMessage.getAssistantMessage());
            }
            singleMetaMessageMap.remove(userId);
            return messages;
        } finally {
            messageLock.unlock();
        }
    }

    @CapabilityMethod
    public Map<String, List<MetaMessage>> drainSingleMetaMessages() {
        messageLock.lock();
        try {
            Map<String, List<MetaMessage>> drained = new HashMap<>();
            for (Map.Entry<String, List<MetaMessage>> entry : singleMetaMessageMap.entrySet()) {
                drained.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            singleMetaMessageMap.clear();
            return drained;
        } finally {
            messageLock.unlock();
        }
    }

    @CapabilityMethod
    public List<MetaMessage> snapshotSingleMetaMessages(String userId) {
        messageLock.lock();
        try {
            List<MetaMessage> metaMessages = singleMetaMessageMap.get(userId);
            if (metaMessages == null) {
                return List.of();
            }
            return List.copyOf(metaMessages);
        } finally {
            messageLock.unlock();
        }
    }

    @CapabilityMethod
    public void refreshMemoryId() {
        currentMemoryId = UUID.randomUUID().toString();
    }

    @CapabilityMethod
    public void resetLastUpdatedTime() {
        lastUpdatedTime = System.currentTimeMillis();
    }

    @Override
    protected String getCoreKey() {
        return "cognation-core";
    }
}
