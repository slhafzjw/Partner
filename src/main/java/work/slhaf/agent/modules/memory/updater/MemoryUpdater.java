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
import work.slhaf.agent.modules.memory.updater.static_extractor.StaticMemoryExtractor;
import work.slhaf.agent.modules.memory.updater.static_extractor.data.StaticMemoryExtractInput;
import work.slhaf.agent.modules.memory.updater.summarizer.MemorySummarizer;
import work.slhaf.agent.modules.memory.updater.summarizer.data.SummarizeInput;
import work.slhaf.agent.modules.memory.updater.summarizer.data.SummarizeResult;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@Slf4j
public class MemoryUpdater implements InteractionModule {

    private static MemoryUpdater memoryUpdater;

    private MemoryManager memoryManager;
    private InteractionThreadPoolExecutor executor;
    private MemorySelectExtractor memorySelectExtractor;
    private MemorySummarizer memorySummarizer;
    private SessionManager sessionManager;
    private StaticMemoryExtractor staticMemoryExtractor;

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
        }
        return memoryUpdater;
    }

    @Override
    public void execute(InteractionContext interactionContext) {
        if (interactionContext.isFinished()) {
            return;
        }
        executor.execute(() -> {
            //如果token 大于阈值，则更新记忆
            JSONObject moduleContext = interactionContext.getModuleContext();
            if (moduleContext.getIntValue("total_token") > 24000) {
                String userId = interactionContext.getUserId();
                HashMap<String, String> singleMemorySummary = new HashMap<>();
                try {
                    //更新单聊记忆以及该场景中对应的确定性记忆，同时从chatMessages中去掉单聊记忆
                    updateSingleChatSlices(userId, singleMemorySummary);
                    //更新多人场景下的记忆及相关的确定性记忆
                    updateMultiChatSlices(userId, singleMemorySummary);
                } catch (InterruptedException | IOException | ClassNotFoundException e) {
                    log.error("记忆更新线程出错: {}", e.getLocalizedMessage());
                }
            }
        });

    }

    private void updateMultiChatSlices(String userId, HashMap<String, String> singleMemorySummary) throws InterruptedException, IOException, ClassNotFoundException {
        //此时chatMessages中不再包含单聊记录，直接执行摘要以及切片插入
        //对剩下的多人聊天记录进行进行摘要
        executor.execute(() -> {
            try {
                SummarizeResult summarizeResult = memorySummarizer.execute(new SummarizeInput(memoryManager.getChatMessages(), memoryManager.getTopicTree()));
                MemorySlice memorySlice = getMemorySlice(userId, summarizeResult, memoryManager.getChatMessages());
                //设置involvedUserId
                setInvolvedUserId(userId,memorySlice,memoryManager.getChatMessages());
                memoryManager.insertSlice(memorySlice, summarizeResult.getTopicPath());
                //更新总dialogMap
                singleMemorySummary.put("total", summarizeResult.getSummary());
                memoryManager.updateDialogMap(LocalDateTime.now(), memorySummarizer.executeTotalSummary(singleMemorySummary));
            } catch (IOException | ClassNotFoundException | InterruptedException e) {
                log.error("多人场景记忆更新失败: {}", e.getLocalizedMessage());
            }
        });
    }

private void setInvolvedUserId(String startUserId, MemorySlice memorySlice, List<Message> chatMessages) {
    for (Message chatMessage : chatMessages) {
        if (chatMessage.getRole().equals(ChatConstant.Character.ASSISTANT)) {
            continue;
        }
        //匹配userId
        String content = chatMessage.getContent();
        Pattern pattern = Pattern.compile("\\[.*\\(([^)]+)\\)\\]");
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            continue;
        }
        String userId = matcher.group(1);
        if (userId.equals(startUserId)){
            continue;
        }
        memorySlice.getInvolvedUserIds().add(userId);
    }
}


    private void updateSingleChatSlices(String interactionContext, HashMap<String, String> singleMemorySummary) throws InterruptedException {
        //更新单聊记忆，同时从chatMessages中去掉单聊记忆
        Set<String> userIdSet = new HashSet<>(sessionManager.getSingleMetaMessageMap().keySet());
        List<Callable<Void>> tasks = new ArrayList<>();
        //多人聊天？
        for (String id : userIdSet) {
            List<Message> messages = sessionManager.unpackAndClear(id);
            tasks.add(() -> {
                try {
                    //单聊记忆更新
                    SummarizeResult summarizeResult = memorySummarizer.execute(new SummarizeInput(messages, memoryManager.getTopicTree()));
                    MemorySlice memorySlice = getMemorySlice(interactionContext, summarizeResult, messages);
                    //插入时userDialogMap已经进行更新
                    memoryManager.insertSlice(memorySlice, summarizeResult.getTopicPath());
                    //从chatMessages中移除单聊记录
                    memoryManager.cleanMessage(messages);
                    //添加至singleMemorySummary
                    singleMemorySummary.put(id, summarizeResult.getSummary());
                } catch (Exception e) {
                    log.error("单聊记忆更新出错: {}", e.getLocalizedMessage());
                }
                return null;
            });

            tasks.add(() -> {
                StaticMemoryExtractInput input = StaticMemoryExtractInput.builder()
                        .userId(id)
                        .messages(messages)
                        .existedStaticMemory(memoryManager.getStaticMemory(id))
                        .build();
                Map<String, String> staticMemoryResult = staticMemoryExtractor.execute(input);
                memoryManager.insertStaticMemory(id, staticMemoryResult);
                return null;
            });
        }
        executor.invokeAll(tasks);
    }

    private static MemorySlice getMemorySlice(String userId, SummarizeResult summarizeResult, List<Message> chatMessages) {
        MemorySlice memorySlice = new MemorySlice();
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
