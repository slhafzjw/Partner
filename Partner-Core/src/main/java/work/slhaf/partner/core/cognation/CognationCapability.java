package work.slhaf.partner.core.cognation;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.api.agent.factory.capability.annotation.ToCoordinated;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.api.chat.pojo.MetaMessage;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;

@Capability("cognation")
public interface CognationCapability {

    List<Message> getChatMessages();
    void cleanMessage(List<Message> messages);
    Lock getMessageLock();
    void addMetaMessage(String userId, MetaMessage metaMessage);
    List<Message> unpackAndClear(String userId);
    void refreshMemoryId();
    void resetLastUpdatedTime();
    long getLastUpdatedTime();
    HashMap<String,List<MetaMessage>> getSingleMetaMessageMap();
    String getCurrentMemoryId();

    @ToCoordinated
    boolean isSingleUser();
}
