package work.slhaf.partner.module.modules.memory.updater;

import com.alibaba.fastjson2.JSONObject;
import kotlin.Unit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.annotation.Init;
import work.slhaf.partner.api.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.api.chat.pojo.MetaMessage;
import work.slhaf.partner.common.thread.InteractionThreadPoolExecutor;
import work.slhaf.partner.core.action.entity.Schedulable;
import work.slhaf.partner.core.action.entity.StateAction;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.module.common.module.PostRunningAgentModule;
import work.slhaf.partner.module.modules.action.scheduler.ActionScheduler;
import work.slhaf.partner.module.modules.memory.updater.summarizer.MultiSummarizer;
import work.slhaf.partner.module.modules.memory.updater.summarizer.SingleSummarizer;
import work.slhaf.partner.module.modules.memory.updater.summarizer.TotalSummarizer;
import work.slhaf.partner.module.modules.memory.updater.summarizer.entity.SummarizeInput;
import work.slhaf.partner.module.modules.memory.updater.summarizer.entity.SummarizeResult;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static work.slhaf.partner.common.util.ExtractUtil.extractUserId;

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
    @InjectCapability
    private PerceiveCapability perceiveCapability;

    @InjectModule
    private MultiSummarizer multiSummarizer;
    @InjectModule
    private SingleSummarizer singleSummarizer;
    @InjectModule
    private TotalSummarizer totalSummarizer;
    private final AtomicBoolean updating = new AtomicBoolean(false);

    private InteractionThreadPoolExecutor executor;
    @InjectModule
    private ActionScheduler actionScheduler;

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
            // 如果token 大于阈值，则更新记忆
            JSONObject moduleContext = context.getModuleContext().getExtraContext();
            boolean recall = moduleContext.getBoolean("recall");
            if (recall) {
                log.debug("[MemoryUpdater] 存在回忆");
                int recallCount = moduleContext.getIntValue("recall_count");
                log.debug("[MemoryUpdater] 记忆切片数量 [{}]", recallCount);
            }
            boolean trigger = context.getModuleContext().getExtraContext().getBoolean("post_process_trigger");
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
        long lastUpdatedTime = cognationCapability.getLastUpdatedTime();
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
                cognationCapability.refreshMemoryId();
            }
        } catch (Exception e) {
            log.error("[MemoryUpdater] 记忆更新线程出错: ", e);
        } finally {
            updating.set(false);
        }
    }

    private void updateMemory(List<Message> chatSnapshot) {
        log.debug("[MemoryUpdater] 记忆更新流程开始...");
        Map<String, String> singleMemorySummary = new ConcurrentHashMap<>();
        Map<String, List<Message>> singleChatMessages = drainSingleChatMessages();
        // 更新单聊记忆，同时从chatMessages中去掉单聊记忆
        updateSingleChatSlices(singleMemorySummary, singleChatMessages);
        // 更新多人场景下的记忆及相关的确定性记忆
        List<Message> multiChatMessages = excludeSingleChatMessages(chatSnapshot, singleChatMessages);
        updateMultiChatSlices(singleMemorySummary, multiChatMessages);
        cognationCapability.resetLastUpdatedTime();
        log.debug("[MemoryUpdater] 记忆更新流程结束...");
    }

    private Map<String, List<Message>> drainSingleChatMessages() {
        Map<String, List<Message>> drainedMessages = new HashMap<>();
        Map<String, List<MetaMessage>> drainedMetaMessages = cognationCapability.drainSingleMetaMessages();
        for (Map.Entry<String, List<MetaMessage>> entry : drainedMetaMessages.entrySet()) {
            List<Message> messages = new ArrayList<>();
            for (MetaMessage metaMessage : entry.getValue()) {
                messages.add(metaMessage.getUserMessage());
                messages.add(metaMessage.getAssistantMessage());
            }
            drainedMessages.put(entry.getKey(), messages);
        }
        return drainedMessages;
    }

    private List<Message> excludeSingleChatMessages(List<Message> chatSnapshot, Map<String, List<Message>> singleChatMessages) {
        Set<Message> singleMessages = new HashSet<>();
        for (List<Message> messages : singleChatMessages.values()) {
            singleMessages.addAll(messages);
        }
        return chatSnapshot.stream()
                .filter(message -> !singleMessages.contains(message))
                .toList();
    }

    private void updateMultiChatSlices(Map<String, String> singleMemorySummary, List<Message> multiChatMessages) {
        // 此时chatMessages中不再包含单聊记录，直接执行摘要以及切片插入
        // 对剩下的多人聊天记录进行进行摘要
        Callable<Void> task = () -> {
            log.debug("[MemoryUpdater] 多人聊天记忆更新流程开始...");
            List<Message> chatMessages = getCleanedMessages(multiChatMessages);
            if (!chatMessages.isEmpty()) {
                log.debug("[MemoryUpdater] 存在多人聊天记录, 流程正常进行...");
                // 以第一条user对应的id为发起用户
                String userId = extractUserId(chatMessages.getFirst().getContent());
                if (userId == null) {
                    throw new RuntimeException("未匹配到 userId!");
                }
                SummarizeInput summarizeInput = new SummarizeInput(chatMessages, memoryCapability.getTopicTree());
                log.debug("[MemoryUpdater] 多人聊天记忆更新-总结流程-输入: {}", summarizeInput);
                SummarizeResult summarizeResult = summarize(summarizeInput);
                log.debug("[MemoryUpdater] 多人聊天记忆更新-总结流程-输出: {}", summarizeResult);
                MemorySlice memorySlice = getMemorySlice(userId, summarizeResult, chatMessages);
                // 设置involvedUserId
                setInvolvedUserId(userId, memorySlice, chatMessages);
                memoryCapability.insertSlice(memorySlice, summarizeResult.getTopicPath());
                memoryCapability.updateDialogMap(LocalDateTime.now(), summarizeResult.getSummary());
            } else {
                log.debug("[MemoryUpdater] 不存在多人聊天记录, 将以单聊总结为对话缓存的主要输入: {}", singleMemorySummary);
                memoryCapability.updateDialogMap(LocalDateTime.now(), totalSummarizer.execute(new HashMap<>(singleMemorySummary)));
            }
            log.debug("[MemoryUpdater] 对话缓存更新完毕");
            log.debug("[MemoryUpdater] 多人聊天记忆更新流程结束...");
            return null;
        };
        executor.invokeAll(List.of(task));
    }

    // TODO need to move time information into perceive core
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

    private void setInvolvedUserId(String startUserId, MemorySlice memorySlice, List<Message> chatMessages) {
        for (Message chatMessage : chatMessages) {
            if (chatMessage.getRole() == Message.Character.ASSISTANT) {
                continue;
            }
            // 匹配userId
            String userId = extractUserId(chatMessage.getContent());
            if (userId == null) {
                continue;
            }
            if (userId.equals(startUserId)) {
                continue;
            }
            memorySlice.setInvolvedUserIds(new ArrayList<>());
            memorySlice.getInvolvedUserIds().add(userId);
        }
    }

    private void updateSingleChatSlices(Map<String, String> singleMemorySummary, Map<String, List<Message>> singleChatMessages) {
        log.debug("[MemoryUpdater] 单聊记忆更新流程开始...");
        List<Callable<Void>> tasks = new ArrayList<>();
        AtomicInteger count = new AtomicInteger(0);
        for (Map.Entry<String, List<Message>> entry : singleChatMessages.entrySet()) {
            String id = entry.getKey();
            List<Message> messages = entry.getValue();
            if (messages.isEmpty()) {
                continue;
            }
            tasks.add(() -> {
                int thisCount = count.incrementAndGet();
                log.debug("[MemoryUpdater] 单聊记忆[{}]更新: {}", thisCount, id);
                try {
                    // 单聊记忆更新
                    SummarizeInput summarizeInput = new SummarizeInput(messages, memoryCapability.getTopicTree());
                    log.debug("[MemoryUpdater] 单聊记忆[{}]更新-总结流程-输入: {}", thisCount, JSONObject.toJSONString(summarizeInput));
                    SummarizeResult summarizeResult = summarize(summarizeInput);
                    log.debug("[MemoryUpdater] 单聊记忆[{}]更新-总结流程-输出: {}", thisCount, JSONObject.toJSONString(summarizeResult));
                    MemorySlice memorySlice = getMemorySlice(id, summarizeResult, messages);
                    // 插入时userDialogMap已经进行更新
                    memoryCapability.insertSlice(memorySlice, summarizeResult.getTopicPath());
                    // 从chatMessages中移除单聊记录
                    cognationCapability.cleanMessage(messages);
                    // 添加至singleMemorySummary
                    String key = perceiveCapability.getUser(id).getNickName() + "[" + id + "]";
                    singleMemorySummary.put(key, summarizeResult.getSummary());
                    log.debug("[MemoryUpdater] 单聊记忆[{}]更新成功: ", thisCount);
                } catch (Exception e) {
                    log.error("[MemoryUpdater] 单聊记忆[{}]更新出错: ", thisCount, e);
                }
                return null;
            });
        }
        executor.invokeAll(tasks);
        log.debug("[MemoryUpdater] 单聊记忆更新结束...");
    }

    private SummarizeResult summarize(SummarizeInput summarizeInput) {
        singleSummarizer.execute(summarizeInput.getChatMessages());
        return multiSummarizer.execute(summarizeInput);
    }

    private MemorySlice getMemorySlice(String userId, SummarizeResult summarizeResult, List<Message> chatMessages) {
        MemorySlice memorySlice = new MemorySlice();
        // 设置 memoryId,timestamp
        memorySlice.setMemoryId(cognationCapability.getCurrentMemoryId());
        memorySlice.setTimestamp(System.currentTimeMillis());
        // 补充信息
        memorySlice.setPrivate(summarizeResult.isPrivate());
        memorySlice.setSummary(summarizeResult.getSummary());
        memorySlice.setChatMessages(chatMessages);
        memorySlice.setStartUserId(userId);
        List<List<String>> relatedTopicPathList = new ArrayList<>();
        for (String string : summarizeResult.getRelatedTopicPath()) {
            List<String> list = Arrays.stream(string.split("->")).toList();
            relatedTopicPathList.add(list);
        }
        memorySlice.setRelatedTopics(relatedTopicPathList);
        return memorySlice;
    }

    @Override
    public int order() {
        return 7;
    }
}
