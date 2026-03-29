package work.slhaf.partner.module.memory.updater;

import com.alibaba.fastjson2.JSONObject;
import kotlin.Unit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.annotation.Init;
import work.slhaf.partner.api.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.common.thread.InteractionThreadPoolExecutor;
import work.slhaf.partner.core.action.entity.Schedulable;
import work.slhaf.partner.core.action.entity.StateAction;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.module.action.scheduler.ActionScheduler;
import work.slhaf.partner.module.communication.DialogRollingService;
import work.slhaf.partner.module.memory.runtime.MemoryRuntime;
import work.slhaf.partner.module.memory.updater.summarizer.MultiSummarizer;
import work.slhaf.partner.module.memory.updater.summarizer.SingleSummarizer;
import work.slhaf.partner.module.memory.updater.summarizer.entity.SummarizeInput;
import work.slhaf.partner.module.memory.updater.summarizer.entity.SummarizeResult;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    private InteractionThreadPoolExecutor executor;

    @Init
    public void init() {
        executor = InteractionThreadPoolExecutor.getInstance();
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
            List<Message> chatSnapshot = cognitionCapability.snapshotChatMessages();
            if (chatSnapshot.size() <= 1) {
                return;
            }

            RollingRecord record = updateMemory(chatSnapshot);
            if (record != null) {
                dialogRollingService.rollMessages(chatSnapshot, chatSnapshot.size(), CONTEXT_RETAIN_DIVISOR, record.unitId, record.sliceId, record.summary);
            }

            if (refreshMemoryId) {
                memoryCapability.refreshMemorySession();
            }
        } catch (Exception e) {
            log.error("[MemoryUpdater] 记忆更新线程出错: ", e);
        } finally {
            updating.set(false);
        }
    }

    private RollingRecord updateMemory(List<Message> chatSnapshot) {
        log.debug("[MemoryUpdater] 记忆更新流程开始...");
        if (chatSnapshot.isEmpty()) {
            return null;
        }
        SummarizeInput summarizeInput = new SummarizeInput(chatSnapshot, memoryRuntime.getTopicTree());
        log.debug("[MemoryUpdater] 记忆更新-总结流程-输入: {}", JSONObject.toJSONString(summarizeInput));
        SummarizeResult summarizeResult = summarize(summarizeInput);
        log.debug("[MemoryUpdater] 记忆更新-总结流程-输出: {}", JSONObject.toJSONString(summarizeResult));
        MemoryUnit memoryUnit = buildMemoryUnit(chatSnapshot, summarizeResult);
        memoryRuntime.recordMemory(
                memoryUnit,
                summarizeResult.getTopicPath(),
                summarizeResult.getRelatedTopicPath(),
                summarizeResult.getSummary()
        );
        log.debug("[MemoryUpdater] 记忆更新流程结束...");
        MemorySlice newSlice = memoryUnit.getSlices().getLast();
        return new RollingRecord(memoryUnit.getId(), newSlice.getId(), newSlice.getSummary());
    }

    private SummarizeResult summarize(SummarizeInput summarizeInput) {
        singleSummarizer.execute(summarizeInput.getChatMessages());
        return multiSummarizer.execute(summarizeInput);
    }

    private MemoryUnit buildMemoryUnit(List<Message> chatMessages, SummarizeResult summarizeResult) {
        long now = System.currentTimeMillis();
        String memoryId = memoryCapability.getMemorySessionId();
        String resolvedMemoryId = memoryId == null || memoryId.isBlank() ? UUID.randomUUID().toString() : memoryId;
        MemoryUnit existingUnit = memoryCapability.getMemoryUnit(resolvedMemoryId);
        List<Message> existingMessages = existingUnit != null && existingUnit.getConversationMessages() != null
                ? existingUnit.getConversationMessages()
                : List.of();
        int startIndex = existingMessages.size();

        MemorySlice memorySlice = new MemorySlice();
        memorySlice.setId(UUID.randomUUID().toString());
        memorySlice.setStartIndex(startIndex);
        memorySlice.setEndIndex(startIndex + chatMessages.size());
        memorySlice.setSummary(summarizeResult.getSummary());
        memorySlice.setTimestamp(now);

        MemoryUnit memoryUnit = new MemoryUnit();
        memoryUnit.setId(resolvedMemoryId);
        memoryUnit.setTimestamp(now);
        List<Message> conversationMessages = new ArrayList<>(existingMessages);
        conversationMessages.addAll(chatMessages);
        memoryUnit.setConversationMessages(conversationMessages);

        List<MemorySlice> slices = existingUnit != null && existingUnit.getSlices() != null
                ? new ArrayList<>(existingUnit.getSlices())
                : new ArrayList<>();
        slices.add(memorySlice);
        memoryUnit.setSlices(slices);
        return memoryUnit;
    }

    @Override
    public int order() {
        return 7;
    }

    private record RollingRecord(String unitId, String sliceId, String summary) {
    }
}
