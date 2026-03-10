package work.slhaf.partner.module.modules.memory.updater;

import com.alibaba.fastjson2.JSONObject;
import kotlin.Unit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
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
import work.slhaf.partner.core.memory.pojo.SliceRef;
import work.slhaf.partner.module.common.module.PostRunningAgentModule;
import work.slhaf.partner.module.modules.action.scheduler.ActionScheduler;
import work.slhaf.partner.module.modules.memory.runtime.MemoryRuntime;
import work.slhaf.partner.module.modules.memory.updater.summarizer.MultiSummarizer;
import work.slhaf.partner.module.modules.memory.updater.summarizer.SingleSummarizer;
import work.slhaf.partner.module.modules.memory.updater.summarizer.entity.SummarizeInput;
import work.slhaf.partner.module.modules.memory.updater.summarizer.entity.SummarizeResult;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemoryUpdater extends PostRunningAgentModule {

    private static final String AUTO_UPDATE_CRON = "0/10 * * * * ?";
    private static final long UPDATE_TRIGGER_INTERVAL = 60 * 60 * 1000;
    private static final int CONTEXT_RETAIN_DIVISOR = 6;

    @InjectCapability
    private CognationCapability cognationCapability;
    @InjectCapability
    private MemoryCapability memoryCapability;

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
    private volatile long lastUpdatedTime;

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
    public void doExecute(PartnerRunningFlowContext context) {
        executor.execute(() -> {
            JSONObject moduleContext = context.getModuleContext().getExtraContext();
            boolean recall = moduleContext.getBoolean("recall");
            if (recall) {
                int recallCount = moduleContext.getIntValue("recall_count");
                log.debug("[MemoryUpdater] 当前激活记忆数量 [{}]", recallCount);
            }
            boolean trigger = moduleContext.getBoolean("post_process_trigger");
            if (!trigger) {
                return;
            }
            log.debug("[MemoryUpdater] 记忆更新触发");
            triggerMemoryUpdate(false);
        });
    }

    @Override
    protected boolean relyOnMessage() {
        return true;
    }

    private void tryAutoUpdate() {
        long currentTime = System.currentTimeMillis();
        int chatCount = cognationCapability.snapshotChatMessages().size();
        if (lastUpdatedTime != 0 && currentTime - lastUpdatedTime > UPDATE_TRIGGER_INTERVAL && chatCount > 1) {
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
                memoryCapability.refreshMemoryId();
            }
        } catch (Exception e) {
            log.error("[MemoryUpdater] 记忆更新线程出错: ", e);
        } finally {
            updating.set(false);
        }
    }

    private void updateMemory(List<Message> chatSnapshot) {
        log.debug("[MemoryUpdater] 记忆更新流程开始...");
        List<Message> chatMessages = getCleanedMessages(chatSnapshot);
        if (chatMessages.isEmpty()) {
            return;
        }
        SummarizeInput summarizeInput = new SummarizeInput(chatMessages, memoryRuntime.getTopicTree());
        log.debug("[MemoryUpdater] 记忆更新-总结流程-输入: {}", JSONObject.toJSONString(summarizeInput));
        SummarizeResult summarizeResult = summarize(summarizeInput);
        log.debug("[MemoryUpdater] 记忆更新-总结流程-输出: {}", JSONObject.toJSONString(summarizeResult));
        MemoryUnit memoryUnit = buildMemoryUnit(chatMessages, summarizeResult);
        memoryCapability.saveMemoryUnit(memoryUnit);
        MemorySlice memorySlice = memoryUnit.getSlices().getFirst();
        SliceRef sliceRef = new SliceRef(memoryUnit.getId(), memorySlice.getId());
        bindTopics(memoryUnit, summarizeResult, sliceRef);
        memoryRuntime.updateDialogMap(LocalDateTime.now(), summarizeResult.getSummary());
        lastUpdatedTime = System.currentTimeMillis();
        log.debug("[MemoryUpdater] 记忆更新流程结束...");
    }

    private void bindTopics(MemoryUnit memoryUnit, SummarizeResult summarizeResult, SliceRef sliceRef) {
        memoryRuntime.indexMemoryUnit(memoryUnit);
        memoryRuntime.bindTopic(summarizeResult.getTopicPath(), sliceRef);
        if (summarizeResult.getRelatedTopicPath() == null) {
            return;
        }
        for (String relatedTopicPath : summarizeResult.getRelatedTopicPath()) {
            memoryRuntime.bindTopic(relatedTopicPath, sliceRef);
        }
    }

    private List<Message> getCleanedMessages(List<Message> chatMessages) {
        return chatMessages.stream()
                .map(message -> {
                    if (message.getRole() == Message.Character.ASSISTANT) {
                        return message;
                    }
                    List<String> splitResult = Arrays.stream(message.getContent().split("\\*\\*")).toList();
                    if (splitResult.isEmpty()) {
                        return message;
                    }
                    String time = splitResult.getLast();
                    return new Message(Message.Character.USER, message.getContent().replace("\r\n**" + time, ""));
                }).toList();
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
        String memoryId = memoryCapability.getCurrentMemoryId();
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
