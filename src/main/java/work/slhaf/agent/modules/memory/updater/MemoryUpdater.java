package work.slhaf.agent.modules.memory.updater;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.constant.ChatConstant;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.InteractionThreadPoolExecutor;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.memory.MemoryManager;
import work.slhaf.agent.core.memory.pojo.MemorySlice;
import work.slhaf.agent.core.session.SessionManager;
import work.slhaf.agent.modules.memory.selector.extractor.MemorySelectExtractor;
import work.slhaf.agent.modules.memory.updater.exception.UnExpectedMessageCountException;
import work.slhaf.agent.modules.memory.updater.static_extractor.StaticMemoryExtractor;
import work.slhaf.agent.modules.memory.updater.static_extractor.data.StaticMemoryExtractInput;
import work.slhaf.agent.modules.memory.updater.summarizer.MemorySummarizer;
import work.slhaf.agent.modules.memory.updater.summarizer.data.SummarizeInput;
import work.slhaf.agent.modules.memory.updater.summarizer.data.SummarizeResult;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@Slf4j
public class MemoryUpdater implements InteractionModule {

    private static MemoryUpdater memoryUpdater;

    private static final String USERID_REGEX = "\\[.*\\(([^)]+)\\)\\]";
    private static final long SCHEDULED_UPDATE_INTERVAL = 10 * 1000;
    private static final long UPDATE_TRIGGER_INTERVAL = 30 * 60 * 1000;
    //    private static final int TRIGGER_TOKEN_LIMIT = 5 * 1000;
    private static final int TOKEN_PER_RECALL = 230;
    private static final int TRIGGER_ROLL_LIMIT = 12;

    private MemoryManager memoryManager;
    private InteractionThreadPoolExecutor executor;
    private MemorySelectExtractor memorySelectExtractor;
    private MemorySummarizer memorySummarizer;
    private SessionManager sessionManager;
    private StaticMemoryExtractor staticMemoryExtractor;
    private int moduleMessageCount = 0;

    private MemoryUpdater() {
    }

    public static MemoryUpdater getInstance() throws IOException, ClassNotFoundException {
        if (memoryUpdater == null) {
            memoryUpdater = new MemoryUpdater();
            memoryUpdater.setMemoryManager(MemoryManager.getInstance());
            memoryUpdater.setMemorySelectExtractor(MemorySelectExtractor.getInstance());
            memoryUpdater.setMemorySummarizer(MemorySummarizer.getInstance());
            memoryUpdater.setSessionManager(SessionManager.getInstance());
            memoryUpdater.setStaticMemoryExtractor(StaticMemoryExtractor.getInstance());
            memoryUpdater.setExecutor(InteractionThreadPoolExecutor.getInstance());

            memoryUpdater.setScheduledUpdater();
        }
        return memoryUpdater;
    }

    private void setScheduledUpdater() {
        executor.execute(() -> {
            log.info("[MemoryUpdater] 记忆自动更新线程启动");
            while (!Thread.interrupted()) {
                try {
                    long currentTime = System.currentTimeMillis();
                    long lastUpdatedTime = sessionManager.getLastUpdatedTime();
                    int chatCount = memoryManager.getChatMessages().size();
                    if (lastUpdatedTime != 0 && currentTime - lastUpdatedTime > UPDATE_TRIGGER_INTERVAL && chatCount > 1) {
                        updateMemory();
                        //重置MemoryId
                        sessionManager.refreshMemoryId();
                        log.info("[MemoryUpdater] 记忆更新: 自动触发");
                    }
                    Thread.sleep(SCHEDULED_UPDATE_INTERVAL);
                } catch (Exception e) {
                    log.error("[MemoryUpdater] 记忆自动更新线程出错: {}", e.getLocalizedMessage());
                }
            }
            log.info("[MemoryUpdater] 记忆自动更新线程结束");
        });
    }

    @Override
    public void execute(InteractionContext interactionContext) {
        if (interactionContext.isFinished()) {
            log.warn("[MemoryUpdater] 流程强制结束, 不触发记忆被动更新机制");
            return;
        }
        executor.execute(() -> {
            //如果token 大于阈值，则更新记忆
            JSONObject moduleContext = interactionContext.getModuleContext();
            boolean recall = moduleContext.getBoolean("recall");
            if (recall) {
                log.debug("[MemoryUpdater] 存在回忆");
                int recallCount = moduleContext.getIntValue("recall_count");
                log.debug("[MemoryUpdater] 记忆切片数量 [{}]", recallCount);
            }
            int messageCount = moduleContext.getIntValue("message_count");
            updateModuleMessageCount(messageCount);
            if (messageCount > TRIGGER_ROLL_LIMIT) {
                try {
                    log.debug("[MemoryUpdater] 记忆更新: token超限");
                    updateMemory();
                } catch (Exception e) {
                    log.error("[MemoryUpdater] 记忆更新线程出错: {}", e.getLocalizedMessage());
                }
            }
        });
        sessionManager.resetLastUpdatedTime();
    }

    private void updateModuleMessageCount(int messageCount) {
        int totalMessageCount = memoryManager.getChatMessages().size();
        moduleMessageCount = totalMessageCount - messageCount;
    }

    private void updateMemory() {
        log.debug("[MemoryUpdater] 记忆更新流程开始...");
        HashMap<String, String> singleMemorySummary = new HashMap<>();
        //更新单聊记忆以及该场景中对应的确定性记忆，同时从chatMessages中去掉单聊记忆
        updateSingleChatSlices(singleMemorySummary);
        //更新多人场景下的记忆及相关的确定性记忆
        updateMultiChatSlices(singleMemorySummary);
        //清空chatMessages
        clearChatMessages();
    }

    private void updateMultiChatSlices(HashMap<String, String> singleMemorySummary) {
        //此时chatMessages中不再包含单聊记录，直接执行摘要以及切片插入
        //对剩下的多人聊天记录进行进行摘要
        executor.execute(() -> {
            log.debug("[MemoryUpdater] 多人聊天记忆更新流程开始...");
            try {
                List<Message> chatMessages = new ArrayList<>(memoryManager.getChatMessages());
                chatMessages.removeFirst();
                if (!chatMessages.isEmpty()) {
                    log.debug("[MemoryUpdater] 存在多人聊天记录, 流程正常进行...");
                    //以第一条user对应的id为发起用户
                    Pattern pattern = Pattern.compile(USERID_REGEX);
                    Matcher matcher = pattern.matcher(chatMessages.getFirst().getContent());
                    if (!matcher.find()) {
                        throw new RuntimeException("未匹配到 userId!");
                    }
                    String userId = matcher.group(1);
                    SummarizeInput summarizeInput = new SummarizeInput(chatMessages, memoryManager.getTopicTree());
                    log.debug("[MemoryUpdater] 多人聊天记忆更新-总结流程-输入: {}", summarizeInput);
                    SummarizeResult summarizeResult = memorySummarizer.execute(summarizeInput);
                    log.debug("[MemoryUpdater] 多人聊天记忆更新-总结流程-输出: {}", summarizeResult);
                    MemorySlice memorySlice = getMemorySlice(userId, summarizeResult, chatMessages);
                    //设置involvedUserId
                    setInvolvedUserId(userId, memorySlice, chatMessages);
                    memoryManager.insertSlice(memorySlice, summarizeResult.getTopicPath());

                    memoryManager.updateDialogMap(LocalDateTime.now(), summarizeResult.getSummary());

                } else {
                    log.debug("[MemoryUpdater] 不存在多人聊天记录, 将以单聊总结为对话缓存的主要输入: {}", singleMemorySummary);
                    memoryManager.updateDialogMap(LocalDateTime.now(), memorySummarizer.executeTotalSummary(singleMemorySummary));
                }
                log.debug("[MemoryUpdater] 对话缓存更新完毕");
                log.debug("[MemoryUpdater] 多人聊天记忆更新流程结束...");
            } catch (IOException | ClassNotFoundException | InterruptedException e) {
                log.error("[MemoryUpdater] 多人场景记忆更新失败: ", e);
            }
        });
    }

    private void clearChatMessages() {
        if (moduleMessageCount < 1) {
            throw new UnExpectedMessageCountException("ModuleMessageCount 异常: " + moduleMessageCount);
        }
        memoryManager.setChatMessages(memoryManager.getChatMessages().subList(0, moduleMessageCount - 1));
    }

    private void setInvolvedUserId(String startUserId, MemorySlice memorySlice, List<Message> chatMessages) {
        for (Message chatMessage : chatMessages) {
            if (chatMessage.getRole().equals(ChatConstant.Character.ASSISTANT)) {
                continue;
            }
            //匹配userId
            String content = chatMessage.getContent();
            Pattern pattern = Pattern.compile(USERID_REGEX);
            Matcher matcher = pattern.matcher(content);
            if (!matcher.find()) {
                continue;
            }
            String userId = matcher.group(1);
            if (userId.equals(startUserId)) {
                continue;
            }
            memorySlice.setInvolvedUserIds(new ArrayList<>());
            memorySlice.getInvolvedUserIds().add(userId);
        }
    }


    private void updateSingleChatSlices(HashMap<String, String> singleMemorySummary) {
        log.debug("[MemoryUpdater] 单聊记忆更新流程开始...");
        //更新单聊记忆，同时从chatMessages中去掉单聊记忆
        Set<String> userIdSet = new HashSet<>(sessionManager.getSingleMetaMessageMap().keySet());
        List<Callable<Void>> tasks = new ArrayList<>();
        //多人聊天？
        AtomicInteger count = new AtomicInteger(0);
        for (String id : userIdSet) {
            List<Message> messages = sessionManager.unpackAndClear(id);
            tasks.add(() -> {
                int thisCount = count.incrementAndGet();
                log.debug("[MemoryUpdater] 单聊记忆[{}]更新: {}", thisCount, id);
                try {
                    //单聊记忆更新
                    SummarizeInput summarizeInput = new SummarizeInput(messages, memoryManager.getTopicTree());
                    log.debug("[MemoryUpdater] 单聊记忆[{}]更新-总结流程-输入: {}", thisCount, summarizeInput);
                    SummarizeResult summarizeResult = memorySummarizer.execute(summarizeInput);
                    log.debug("[MemoryUpdater] 单聊记忆[{}]更新-总结流程-输出: {}", thisCount, summarizeResult);
                    MemorySlice memorySlice = getMemorySlice(id, summarizeResult, messages);
                    //插入时userDialogMap已经进行更新
                    memoryManager.insertSlice(memorySlice, summarizeResult.getTopicPath());
                    //从chatMessages中移除单聊记录
                    memoryManager.cleanMessage(messages);
                    //添加至singleMemorySummary
                    String key = memoryManager.getUser(id).getNickName() + "[" + id + "]";
                    singleMemorySummary.put(key, summarizeResult.getSummary());
                    log.debug("[MemoryUpdater] 单聊记忆[{}]更新成功: ", thisCount);
                } catch (Exception e) {
                    log.error("[MemoryUpdater] 单聊记忆[{}]更新出错: ", thisCount, e);
                }
                return null;
            });

            tasks.add(() -> {
                log.debug("[MemoryUpdater] 静态记忆更新开始...");
                StaticMemoryExtractInput input = StaticMemoryExtractInput.builder()
                        .userId(id)
                        .messages(messages)
                        .existedStaticMemory(memoryManager.getStaticMemory(id))
                        .build();
                log.debug("[MemoryUpdater] 静态记忆更新输入: {}", input);
                Map<String, String> staticMemoryResult = staticMemoryExtractor.execute(input);
                log.debug("[MemoryUpdater] 静态记忆更新结果: {}", staticMemoryResult);
                memoryManager.insertStaticMemory(id, staticMemoryResult);
                return null;
            });
        }
        executor.invokeAll(tasks);
        log.debug("[MemoryUpdater] 单聊记忆更新结束...");
    }

    private MemorySlice getMemorySlice(String userId, SummarizeResult summarizeResult, List<Message> chatMessages) {
        MemorySlice memorySlice = new MemorySlice();
        //设置 memoryId,timestamp
        memorySlice.setMemoryId(sessionManager.getCurrentMemoryId());
        memorySlice.setTimestamp(System.currentTimeMillis());

        //补充信息
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
