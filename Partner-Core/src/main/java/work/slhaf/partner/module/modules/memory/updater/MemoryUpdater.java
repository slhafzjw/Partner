package work.slhaf.partner.module.modules.memory.updater;

import com.alibaba.fastjson2.JSONObject;
import kotlin.Unit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.annotation.Init;
import work.slhaf.partner.api.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.common.thread.InteractionThreadPoolExecutor;
import work.slhaf.partner.core.action.entity.Schedulable;
import work.slhaf.partner.core.action.entity.StateAction;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.module.modules.action.scheduler.ActionScheduler;
import work.slhaf.partner.module.modules.memory.runtime.MemoryRuntime;
import work.slhaf.partner.module.modules.memory.updater.summarizer.MultiSummarizer;
import work.slhaf.partner.module.modules.memory.updater.summarizer.SingleSummarizer;
import work.slhaf.partner.module.modules.memory.updater.summarizer.entity.SummarizeInput;
import work.slhaf.partner.module.modules.memory.updater.summarizer.entity.SummarizeResult;
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
    private CognationCapability cognationCapability;
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
    public void execute(PartnerRunningFlowContext context) {
        boolean trigger = cognationCapability.getChatMessages().size() >= MEMORY_UPDATE_TRIGGER_ROLL_LIMIT;
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
        int chatCount = cognationCapability.snapshotChatMessages().size();
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
            List<Message> chatSnapshot = cognationCapability.snapshotChatMessages();
            if (chatSnapshot.size() <= 1) {
                return;
            }
            updateMemory(chatSnapshot);
            cognationCapability.rollChatMessagesWithSnapshot(chatSnapshot.size(), CONTEXT_RETAIN_DIVISOR);
            if (refreshMemoryId) {
                memoryCapability.refreshMemorySession();
            }
        } catch (Exception e) {
            log.error("[MemoryUpdater] 记忆更新线程出错: ", e);
        } finally {
            updating.set(false);
        }
    }

    private void updateMemory(List<Message> chatSnapshot) {
        log.debug("[MemoryUpdater] 记忆更新流程开始...");
        if (chatSnapshot.isEmpty()) {
            return;
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
    }

    private SummarizeResult summarize(SummarizeInput summarizeInput) {
        singleSummarizer.execute(summarizeInput.getChatMessages());
        return multiSummarizer.execute(summarizeInput);
    }

    private MemoryUnit buildMemoryUnit(List<Message> chatMessages, SummarizeResult summarizeResult) {
        long now = System.currentTimeMillis();
        MemorySlice memorySlice = new MemorySlice();
        memorySlice.setId(UUID.randomUUID().toString());
        memorySlice.setStartIndex(0);
        memorySlice.setEndIndex(Math.max(chatMessages.size() - 1, 0));
        memorySlice.setSummary(summarizeResult.getSummary());
        memorySlice.setTimestamp(now);

        MemoryUnit memoryUnit = new MemoryUnit();
        String memoryId = memoryCapability.getMemorySessionId();
        memoryUnit.setId(memoryId == null || memoryId.isBlank() ? UUID.randomUUID().toString() : memoryId);
        memoryUnit.setTimestamp(now);
        memoryUnit.setConversationMessages(new ArrayList<>(chatMessages));
        memoryUnit.setSlices(new ArrayList<>(List.of(memorySlice)));
        return memoryUnit;
    }

    @Override
    public int order() {
        return 7;
    }
}
