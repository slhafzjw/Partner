package work.slhaf.partner.core.cognation;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.api.chat.pojo.MetaMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;

@Capability("cognation")
public interface CognationCapability {

    String initiateTurn(String input);

    List<Message> getChatMessages();

    List<Message> snapshotChatMessages();

    void rollChatMessagesWithSnapshot(int snapshotSize, int retainDivisor);

    void cleanMessage(List<Message> messages);

    Lock getMessageLock();

    void addMetaMessage(String userId, MetaMessage metaMessage);

    List<Message> unpackAndClear(String userId);

    void refreshMemoryId();

    void resetLastUpdatedTime();

    long getLastUpdatedTime();

    HashMap<String, List<MetaMessage>> getSingleMetaMessageMap();

    Map<String, List<MetaMessage>> drainSingleMetaMessages();

    List<MetaMessage> snapshotSingleMetaMessages(String userId);

    String getCurrentMemoryId();

}
