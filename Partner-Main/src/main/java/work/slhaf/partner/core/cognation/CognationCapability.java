package work.slhaf.partner.core.cognation;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.api.agent.factory.capability.annotation.ToCoordinated;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.submodule.memory.pojo.EvaluatedSlice;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;

@Capability("cognation")
public interface CognationCapability {

    List<Message> getChatMessages();
    void setChatMessages(List<Message> chatMessages);
    void cleanMessage(List<Message> messages);
    void updateActivatedSlices(String userId, List<EvaluatedSlice> memorySlices);
    String getActivatedSlicesStr(String userId);
    HashMap<String, List<EvaluatedSlice>> getActivatedSlices();
    void clearActivatedSlices(String userId);
    boolean hasActivatedSlices(String userId);
    int getActivatedSlicesSize(String userId);
    List<EvaluatedSlice> getActivatedSlices(String userId);
    Lock getMessageLock();

    @ToCoordinated
    boolean isSingleUser();
}
