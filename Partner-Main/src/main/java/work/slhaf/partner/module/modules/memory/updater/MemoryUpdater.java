package work.slhaf.partner.module.modules.memory.updater;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.api.chat.constant.ChatConstant;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.common.thread.InteractionThreadPoolExecutor;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.module.common.module.PostRunningModule;
import work.slhaf.partner.module.modules.memory.updater.summarizer.MultiSummarizer;
import work.slhaf.partner.module.modules.memory.updater.summarizer.SingleSummarizer;
import work.slhaf.partner.module.modules.memory.updater.summarizer.TotalSummarizer;
import work.slhaf.partner.module.modules.memory.updater.summarizer.entity.SummarizeInput;
import work.slhaf.partner.module.modules.memory.updater.summarizer.entity.SummarizeResult;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static work.slhaf.partner.common.util.ExtractUtil.extractUserId;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
@AgentModule(name = "memory_updater", order = 7)
public class MemoryUpdater extends PostRunningModule {

    private static final long SCHEDULED_UPDATE_INTERVAL = 10 * 1000;
    private static final long UPDATE_TRIGGER_INTERVAL = 60 * 60 * 1000;

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

    private InteractionThreadPoolExecutor executor;
    /**
     * 用于临时存储完整对话记录，在MemoryManager的分离后
     */
    private List<Message> tempMessage;

    @Init
    public void init() {
        executor = InteractionThreadPoolExecutor.getInstance();
        setScheduledUpdater();
    }

    private void setScheduledUpdater() {
        executor.execute(() -> {
            log.info("[MemoryUpdater] 记忆自动更新线程启动");
            while (!Thread.interrupted()) {
                try {
                    long currentTime = System.currentTimeMillis();
                    long lastUpdatedTime = cognationCapability.getLastUpdatedTime();
                    int chatCount = cognationCapability.getChatMessages().size();
                    if (lastUpdatedTime != 0 && currentTime - lastUpdatedTime > UPDATE_TRIGGER_INTERVAL && chatCount > 1) {
                        updateMemory();
                        cognationCapability.getChatMessages().clear();
                        // 重置MemoryId
                        cognationCapability.refreshMemoryId();
                        log.info("[MemoryUpdater] 记忆更新: 自动触发");
                    }
                    Thread.sleep(SCHEDULED_UPDATE_INTERVAL);
                } catch (Exception e) {
                    log.error("[MemoryUpdater] 记忆自动更新线程出错: ", e);
                }
            }
            log.info("[MemoryUpdater] 记忆自动更新线程结束");
        });
    }

    @Override
    public void doExecute(PartnerRunningFlowContext context) {
        if (context.isFinished()) {
            log.warn("[MemoryUpdater] 流程强制结束, 不触发记忆被动更新机制");
            return;
        }
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
            try {
                log.debug("[MemoryUpdater] 记忆更新触发");
                updateMemory();
                // 清空chatMessages
                clearChatMessages();
            } catch (Exception e) {
                log.error("[MemoryUpdater] 记忆更新线程出错: ", e);
            }
        });
    }

    private void updateMemory() {
        log.debug("[MemoryUpdater] 记忆更新流程开始...");
        tempMessage = new ArrayList<>(cognationCapability.getChatMessages());
        HashMap<String, String> singleMemorySummary = new HashMap<>();
        // 更新单聊记忆，同时从chatMessages中去掉单聊记忆
        updateSingleChatSlices(singleMemorySummary);
        // 更新多人场景下的记忆及相关的确定性记忆
        updateMultiChatSlices(singleMemorySummary);
        cognationCapability.resetLastUpdatedTime();
        log.debug("[MemoryUpdater] 记忆更新流程结束...");
    }

    private void updateMultiChatSlices(HashMap<String, String> singleMemorySummary) {
        // 此时chatMessages中不再包含单聊记录，直接执行摘要以及切片插入
        // 对剩下的多人聊天记录进行进行摘要
        Callable<Void> task = () -> {
            log.debug("[MemoryUpdater] 多人聊天记忆更新流程开始...");
            List<Message> chatMessages;
            cognationCapability.getMessageLock().lock();
            chatMessages = new ArrayList<>(cognationCapability.getChatMessages());
            cognationCapability.getMessageLock().unlock();
            cleanMessage(chatMessages);
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
                memoryCapability.updateDialogMap(LocalDateTime.now(), totalSummarizer.execute(singleMemorySummary));
            }
            log.debug("[MemoryUpdater] 对话缓存更新完毕");
            log.debug("[MemoryUpdater] 多人聊天记忆更新流程结束...");

            return null;
        };
        executor.invokeAll(List.of(task));
    }

    private void cleanMessage(List<Message> chatMessages) {
        // 清理时间标识
        for (Message message : chatMessages) {
            if (message.getRole().equals(ChatConstant.Character.ASSISTANT)) {
                continue;
            }
            String time = Arrays.stream(message.getContent().split("\\*\\*")).toList().getLast();
            message.setContent(message.getContent().replace("\r\n**" + time, ""));
        }
    }

    private void clearChatMessages() {
        // 不全部清空，保留一部分输入防止上下文割裂
        cognationCapability.getMessageLock().lock();
        List<Message> temp = new ArrayList<>(
                tempMessage.subList(tempMessage.size() - tempMessage.size() / 6, tempMessage.size()));
        cognationCapability.getChatMessages().removeAll(tempMessage);
        cognationCapability.getChatMessages().addAll(0, temp);
        cognationCapability.getMessageLock().unlock();
    }

    private void setInvolvedUserId(String startUserId, MemorySlice memorySlice, List<Message> chatMessages) {
        for (Message chatMessage : chatMessages) {
            if (chatMessage.getRole().equals(ChatConstant.Character.ASSISTANT)) {
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

    private void updateSingleChatSlices(HashMap<String, String> singleMemorySummary) {
        log.debug("[MemoryUpdater] 单聊记忆更新流程开始...");
        // 更新单聊记忆，同时从chatMessages中去掉单聊记忆
        Set<String> userIdSet = new HashSet<>(cognationCapability.getSingleMetaMessageMap().keySet());
        List<Callable<Void>> tasks = new ArrayList<>();
        // 多人聊天？
        AtomicInteger count = new AtomicInteger(0);
        for (String id : userIdSet) {
            List<Message> messages = cognationCapability.unpackAndClear(id);
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
}
