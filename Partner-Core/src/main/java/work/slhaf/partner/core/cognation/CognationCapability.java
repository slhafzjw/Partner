package work.slhaf.partner.core.cognation;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.api.chat.pojo.Message;

import java.util.List;
import java.util.concurrent.locks.Lock;

@Capability("cognation")
public interface CognationCapability {

    String initiateTurn(String input);

    List<Message> getChatMessages();

    List<Message> snapshotChatMessages();

    void rollChatMessagesWithSnapshot(int snapshotSize, int retainDivisor);

    Lock getMessageLock();

}
