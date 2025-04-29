package work.slhaf.agent.core.session;

import lombok.Data;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.chat.pojo.MetaMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Data
public class SessionManager {

    private static SessionManager sessionManager;

    private HashMap<String /*startUserId*/, List<MetaMessage>> singleMetaMessageMap;
    private HashMap<String /*startUserId*/, List<MetaMessage>> multiMetaMessageMap;

    public static SessionManager getInstance() {
        if (sessionManager == null) {
            sessionManager = new SessionManager();
            sessionManager.setSingleMetaMessageMap(new HashMap<>());
        }
        return sessionManager;
    }

    public void addMetaMessage(String userId, MetaMessage metaMessage) {
        if  (singleMetaMessageMap.containsKey(userId)) {
            singleMetaMessageMap.get(userId).add(metaMessage);
        } else {
            singleMetaMessageMap.put(userId, new java.util.ArrayList<>());
            singleMetaMessageMap.get(userId).add(metaMessage);
        }
    }

    public List<Message> unpackAndClear(String userId) {
        List<Message> messages = new ArrayList<>();
        for (MetaMessage metaMessage : singleMetaMessageMap.get(userId)) {
            messages.add(metaMessage.getUserMessage());
            messages.add(metaMessage.getAssistantMessage());
        }
        singleMetaMessageMap.remove(userId);
        return messages;
    }

}
