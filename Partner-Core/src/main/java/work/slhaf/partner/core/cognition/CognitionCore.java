package work.slhaf.partner.core.cognition;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.api.agent.runtime.interaction.AgentRuntime;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.PartnerCore;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@CapabilityCore(value = "cognition")
@Getter
@Setter
public class CognitionCore extends PartnerCore<CognitionCore> {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ReentrantLock messageLock = new ReentrantLock();

    /**
     * 主模型的聊天记录
     */
    private List<Message> chatMessages = new ArrayList<>();

    private final ContextWorkspace contextWorkspace = new ContextWorkspace();

    public CognitionCore() throws IOException, ClassNotFoundException {
    }

    @CapabilityMethod
    public ContextWorkspace contextWorkspace() {
        return contextWorkspace;
    }

    @CapabilityMethod
    public void initiateTurn(String input, String target, String... skippedModules) {
        PartnerRunningFlowContext primaryContext = PartnerRunningFlowContext.fromSelf(input);
        primaryContext.setTarget(target);
        if (skippedModules != null) {
            for (String skippedModule : skippedModules) {
                primaryContext.addSkippedModule(skippedModule);
            }
        }
        AgentRuntime.INSTANCE.submit(primaryContext);
    }

    @CapabilityMethod
    public List<Message> getChatMessages() {
        return chatMessages;
    }

    @CapabilityMethod
    public List<Message> snapshotChatMessages() {
        messageLock.lock();
        try {
            return List.copyOf(chatMessages);
        } finally {
            messageLock.unlock();
        }
    }

    @CapabilityMethod
    public void rollChatMessagesWithSnapshot(int snapshotSize, int retainDivisor) {
        messageLock.lock();
        try {
            int safeSnapshotSize = Math.max(0, Math.min(snapshotSize, chatMessages.size()));
            if (safeSnapshotSize == 0) {
                return;
            }
            int safeDivisor = Math.max(retainDivisor, 1);
            int retainCount = safeSnapshotSize / safeDivisor;
            int retainStart = Math.max(0, safeSnapshotSize - retainCount);

            List<Message> rolled = new ArrayList<>(chatMessages.subList(retainStart, safeSnapshotSize));
            if (chatMessages.size() > safeSnapshotSize) {
                rolled.addAll(chatMessages.subList(safeSnapshotSize, chatMessages.size()));
            }
            chatMessages = rolled;
        } finally {
            messageLock.unlock();
        }
    }

    @CapabilityMethod
    public Lock getMessageLock() {
        return messageLock;
    }

    @Override
    protected String getCoreKey() {
        return "cognition-core";
    }
}
