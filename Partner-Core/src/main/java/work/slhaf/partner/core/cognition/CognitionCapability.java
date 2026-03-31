package work.slhaf.partner.core.cognition;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.api.agent.model.pojo.Message;

import java.util.List;
import java.util.concurrent.locks.Lock;

@Capability("cognition")
public interface CognitionCapability {

    void initiateTurn(String input, String target, String... skippedModules);

    ContextWorkspace contextWorkspace();

    List<Message> getChatMessages();

    List<Message> snapshotChatMessages();

    void rollChatMessagesWithSnapshot(int snapshotSize, int retainDivisor);

    Lock getMessageLock();

}
