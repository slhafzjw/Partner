package work.slhaf.partner.module.memory.updater;

import kotlin.Unit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.Schedulable;
import work.slhaf.partner.core.action.entity.StateAction;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;
import work.slhaf.partner.framework.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.module.action.scheduler.ActionScheduler;
import work.slhaf.partner.module.communication.DialogRollingService;
import work.slhaf.partner.module.memory.runtime.MemoryRuntime;
import work.slhaf.partner.module.memory.updater.summarizer.MultiSummarizer;
import work.slhaf.partner.module.memory.updater.summarizer.SingleSummarizer;
import work.slhaf.partner.module.memory.updater.summarizer.entity.SummarizeInput;
import work.slhaf.partner.runtime.PartnerRunningFlowContext;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemoryUpdater extends AbstractAgentModule.Running<PartnerRunningFlowContext> {

    private static final String AUTO_UPDATE_CRON = "0/10 * * * * ?";
    private static final long UPDATE_TRIGGER_INTERVAL = 60 * 60 * 1000;
    private static final int CONTEXT_RETAIN_DIVISOR = 6;
    private static final int MEMORY_UPDATE_TRIGGER_ROLL_LIMIT = 36;

    @InjectCapability
    private CognitionCapability cognitionCapability;
    @InjectCapability
    private MemoryCapability memoryCapability;
    @InjectCapability
    private PerceiveCapability perceiveCapability;
    @InjectCapability
    private ActionCapability actionCapability;

    @InjectModule
    private MemoryRuntime memoryRuntime;
    @InjectModule
    private MultiSummarizer multiSummarizer;
    @InjectModule
    private SingleSummarizer singleSummarizer;
    @InjectModule
    private ActionScheduler actionScheduler;
    @InjectModule
    private DialogRollingService dialogRollingService;

    private final AtomicBoolean updating = new AtomicBoolean(false);
    private ExecutorService executor;

    @Init
    public void init() {
        executor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
        registerScheduledUpdater();
    }

    private void registerScheduledUpdater() {
        StateAction stateAction = new StateAction(
                "system",
                "memory-auto-update",
                "定时检查并触发记忆更新",
                Schedulable.ScheduleType.CYCLE,
                AUTO_UPDATE_CRON,
                new StateAction.Trigger.Call(() -> {
                    tryAutoUpdate();
                    return Unit.INSTANCE;
                })
        );
        actionScheduler.schedule(stateAction);
        log.info("[MemoryUpdater] 记忆自动更新已注册到 ActionScheduler, cron={}", AUTO_UPDATE_CRON);
    }

    @Override
    public void execute(@NotNull PartnerRunningFlowContext context) {
        boolean trigger = cognitionCapability.getChatMessages().size() >= MEMORY_UPDATE_TRIGGER_ROLL_LIMIT;
        if (!trigger) {
            return;
        }
        executor.execute(() -> {
            log.debug("[MemoryUpdater] 记忆更新触发");
            triggerMemoryUpdate(false);
        });
    }

    private void tryAutoUpdate() {
        long currentTime = System.currentTimeMillis();
        int chatCount = cognitionCapability.snapshotChatMessages().size();
        if (currentTime - perceiveCapability.showLastInteract().toEpochMilli() > UPDATE_TRIGGER_INTERVAL && chatCount > 1) {
            triggerMemoryUpdate(true);
            log.info("[MemoryUpdater] 记忆更新: 自动触发");
        }
    }

    private void triggerMemoryUpdate(boolean refreshMemoryId) {
        if (!updating.compareAndSet(false, true)) {
            log.debug("[MemoryUpdater] 更新任务已在执行中，本次触发跳过");
            return;
        }
        try {
            List<Message> fullChatSnapshot = cognitionCapability.snapshotChatMessages();
            if (fullChatSnapshot.size() <= 1) {
                return;
            }
            List<Message> chatIncrement = resolveChatIncrement(fullChatSnapshot);
            if (chatIncrement.isEmpty()) {
                if (refreshMemoryId) {
                    memoryCapability.refreshMemorySession();
                }
                return;
            }

            RollingRecord record = updateMemory(chatIncrement);
            dialogRollingService.rollMessages(chatIncrement, fullChatSnapshot.size(), CONTEXT_RETAIN_DIVISOR, record.unitId, record.sliceId, record.summary);

            if (refreshMemoryId) {
                memoryCapability.refreshMemorySession();
            }
        } catch (Exception e) {
            log.error("[MemoryUpdater] 记忆更新线程出错: ", e);
        } finally {
            updating.set(false);
        }
    }

    private List<Message> resolveChatIncrement(List<Message> fullChatSnapshot) {
        String memoryId = memoryCapability.getMemorySessionId();
        if (memoryId == null || memoryId.isBlank()) {
            return fullChatSnapshot;
        }
        MemoryUnit existingUnit = memoryCapability.getMemoryUnit(memoryId);
        if (existingUnit == null || existingUnit.getConversationMessages() == null || existingUnit.getConversationMessages().isEmpty()) {
            return fullChatSnapshot;
        }
        List<Message> existingMessages = existingUnit.getConversationMessages();
        int maxOverlap = Math.min(existingMessages.size(), fullChatSnapshot.size());
        for (int overlap = maxOverlap; overlap > 0; overlap--) {
            List<Message> existingSuffix = existingMessages.subList(existingMessages.size() - overlap, existingMessages.size());
            List<Message> snapshotPrefix = fullChatSnapshot.subList(0, overlap);
            if (existingSuffix.equals(snapshotPrefix)) {
                return fullChatSnapshot.subList(overlap, fullChatSnapshot.size());
            }
        }
        return fullChatSnapshot;
    }

    private RollingRecord updateMemory(List<Message> chatSnapshot) {
        log.debug("[MemoryUpdater] 记忆更新流程开始...");
        if (chatSnapshot.isEmpty()) {
            return null;
        }
        SummarizeInput summarizeInput = new SummarizeInput(chatSnapshot, memoryRuntime.getTopicTree());
        singleSummarizer.execute(summarizeInput.getChatMessages());
        return multiSummarizer.execute(summarizeInput).fold(
                summarizeResult -> {
                    MemoryUnit memoryUnit = memoryCapability.updateMemoryUnit(chatSnapshot, summarizeResult.getSummary());
                    memoryRuntime.recordMemory(
                            memoryUnit,
                            summarizeResult.getTopicPath(),
                            summarizeResult.getRelatedTopicPath()
                    );
                    MemorySlice newSlice = memoryUnit.getSlices().getLast();
                    return new RollingRecord(memoryUnit.getId(), newSlice.getId(), newSlice.getSummary());
                },
                exp -> {
                    MemoryUnit memoryUnit = memoryCapability.updateMemoryUnit(chatSnapshot, "no summary, due to exception");
                    MemorySlice newSlice = memoryUnit.getSlices().getLast();
                    return new RollingRecord(memoryUnit.getId(), newSlice.getId(), newSlice.getSummary());
                });
    }

    @Override
    public int order() {
        return 7;
    }

    private record RollingRecord(String unitId, String sliceId, String summary) {
    }
}
