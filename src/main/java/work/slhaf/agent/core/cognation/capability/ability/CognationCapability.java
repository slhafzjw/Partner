package work.slhaf.agent.core.cognation.capability.ability;

import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.core.cognation.submodule.memory.pojo.EvaluatedSlice;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;

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
    boolean isSingleUser();
    Lock getMessageLock();
}
