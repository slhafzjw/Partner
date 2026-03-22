package work.slhaf.partner.core.cognition;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.api.chat.pojo.Message;

import java.util.List;
import java.util.concurrent.locks.Lock;

@Capability("cognition")
public interface CognitionCapability {

    String initiateTurn(String input, String target);

    List<Message> getChatMessages();

    List<Message> snapshotChatMessages();

    void rollChatMessagesWithSnapshot(int snapshotSize, int retainDivisor);

    Lock getMessageLock();

}
