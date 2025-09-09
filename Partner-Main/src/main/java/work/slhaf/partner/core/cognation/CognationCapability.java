package work.slhaf.partner.core.cognation;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.api.agent.factory.capability.annotation.ToCoordinated;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.submodule.memory.pojo.EvaluatedSlice;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;

@Capability("cognation")
public interface CognationCapability {

    @CapabilityMethod
    List<Message> getChatMessages();

    @CapabilityMethod
    void setChatMessages(List<Message> chatMessages);

    @CapabilityMethod
    void cleanMessage(List<Message> messages);

    @CapabilityMethod
    void updateActivatedSlices(String userId, List<EvaluatedSlice> memorySlices);

    @CapabilityMethod
    String getActivatedSlicesStr(String userId);

    @CapabilityMethod
    HashMap<String, List<EvaluatedSlice>> getActivatedSlices();

    @CapabilityMethod
    void clearActivatedSlices(String userId);

    @CapabilityMethod
    boolean hasActivatedSlices(String userId);

    @CapabilityMethod
    int getActivatedSlicesSize(String userId);

    @CapabilityMethod
    List<EvaluatedSlice> getActivatedSlices(String userId);

    @CapabilityMethod
    Lock getMessageLock();

    @ToCoordinated
    boolean isSingleUser();
}
