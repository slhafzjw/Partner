package work.slhaf.partner.core.cognation;

import com.alibaba.fastjson2.JSONObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.api.chat.pojo.MetaMessage;
import work.slhaf.partner.core.PartnerCore;

import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
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
    public List<Message> getChatMessages() {
        return chatMessages;
    }

    @CapabilityMethod
    public long getLastUpdatedTime(){
        return lastUpdatedTime;
    }

    @CapabilityMethod
    public HashMap<String,List<MetaMessage>> getSingleMetaMessageMap(){
        return singleMetaMessageMap;
    }

    @CapabilityMethod
    public String getCurrentMemoryId(){
        return currentMemoryId;
    }

    @CapabilityMethod
    public void setChatMessages(List<Message> chatMessages) {
        this.chatMessages = chatMessages;
    }

    @CapabilityMethod
    public void cleanMessage(List<Message> messages) {
        messageLock.lock();
        this.getChatMessages().removeAll(messages);
        messageLock.unlock();

    }

    @CapabilityMethod
    public Lock getMessageLock() {
        return messageLock;
    }

    @CapabilityMethod
    public void addMetaMessage(String userId, MetaMessage metaMessage) {
        log.debug("[{}] 当前会话历史: {}", JSONObject.toJSONString(singleMetaMessageMap));
        if (singleMetaMessageMap.containsKey(userId)) {
            singleMetaMessageMap.get(userId).add(metaMessage);
        } else {
            singleMetaMessageMap.put(userId, new java.util.ArrayList<>());
            singleMetaMessageMap.get(userId).add(metaMessage);
        }
        log.debug("[SessionManager] 会话历史更新: {}", JSONObject.toJSONString(singleMetaMessageMap));
    }

    @CapabilityMethod
    public List<Message> unpackAndClear(String userId) {
        List<Message> messages = new ArrayList<>();
        for (MetaMessage metaMessage : singleMetaMessageMap.get(userId)) {
            messages.add(metaMessage.getUserMessage());
            messages.add(metaMessage.getAssistantMessage());
        }
        singleMetaMessageMap.remove(userId);
        return messages;
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