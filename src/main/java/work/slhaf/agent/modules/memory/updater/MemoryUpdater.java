package work.slhaf.agent.modules.memory.updater;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.InteractionThreadPoolExecutor;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.memory.MemoryManager;
import work.slhaf.agent.core.memory.pojo.MemorySlice;
import work.slhaf.agent.core.session.SessionManager;
import work.slhaf.agent.modules.memory.selector.extractor.MemorySelectExtractor;
import work.slhaf.agent.modules.memory.updater.summarizer.MemorySummarizer;
import work.slhaf.agent.modules.memory.updater.summarizer.data.SummarizeResult;
import work.slhaf.agent.modules.memory.updater.summarizer.data.TotalSummarizeInput;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Data
@Slf4j
public class MemoryUpdater implements InteractionModule {

    private static MemoryUpdater memoryUpdater;

    private ExecutorService updateExecutor;
    private MemoryManager memoryManager;
    private InteractionThreadPoolExecutor executor;
    private MemorySelectExtractor memorySelectExtractor;
    private MemorySummarizer memorySummarizer;
    private SessionManager sessionManager;

    private MemoryUpdater() {
    }

    public static MemoryUpdater getInstance() throws IOException, ClassNotFoundException {
        if (memoryUpdater == null) {
            memoryUpdater = new MemoryUpdater();
            memoryUpdater.setMemoryManager(MemoryManager.getInstance());
            memoryUpdater.setMemorySelectExtractor(MemorySelectExtractor.getInstance());
            memoryUpdater.setMemorySummarizer(MemorySummarizer.getInstance());
            memoryUpdater.setSessionManager(SessionManager.getInstance());
            memoryUpdater.setUpdateExecutor(Executors.newSingleThreadExecutor());
        }
        return memoryUpdater;
    }

    @Override
    public void execute(InteractionContext interactionContext) throws InterruptedException {
        //TODO 需要保持压缩上下文、更新总摘要、更新确定性记忆、总结所有切片后，更新dialogMap
        if (interactionContext.isFinished()) {
            return;
        }
        updateExecutor.execute(() -> {
            //如果token 大于阈值，则更新记忆
            JSONObject moduleContext = interactionContext.getModuleContext();
            if (moduleContext.getIntValue("total_token") > 24000) {
                //更新单聊记忆，同时从chatMessages中去掉单聊记忆
                try {
                    updateSingleChatSlices(interactionContext);
                    //更新多人场景下的记忆
                    updateMultiChatSlices(interactionContext);
                    //更新确定性记忆
                    executor.execute(() -> {

                    });
                } catch (InterruptedException e) {
                    log.error("记忆更新线程出错: {}", e.getLocalizedMessage());
                }

            }

        });

    }

    private void updateMultiChatSlices(InteractionContext interactionContext) {
        //TODO 更新多人场景对话记忆
        //此时chatMessages中不再包含单聊记录，直接执行摘要以及切片插入

    }

    private void updateSingleChatSlices(InteractionContext interactionContext) throws InterruptedException {
        //更新单聊记忆，同时从chatMessages中去掉单聊记忆
        Set<String> userIdSet = new HashSet<>(sessionManager.getSingleMetaMessageMap().keySet());
        List<Callable<Void>> tasks = new ArrayList<>();
        //多人聊天？
        for (String id : userIdSet) {
            tasks.add(() -> {
                List<Message> messages = sessionManager.unpackAndClear(id);
                try {
                    SummarizeResult summarizeResult = memorySummarizer.execute(new TotalSummarizeInput(messages, memoryManager.getTopicTree()));
                    MemorySlice memorySlice = getMemorySlice(interactionContext, summarizeResult, messages);
                    memoryManager.insertSlice(memorySlice, summarizeResult.getTopicPath());
                    //从chatMessages中移除单聊记录
                    memoryManager.cleanMessage(messages);
                } catch (Exception e) {
                    log.error("记忆更新出错: {}", e.getLocalizedMessage());
                }
                return null;
            });
        }
        executor.invokeAll(tasks);
    }

    private static MemorySlice getMemorySlice(InteractionContext interactionContext, SummarizeResult summarizeResult, List<Message> chatMessages) {
        MemorySlice memorySlice = new MemorySlice();
        memorySlice.setPrivate(summarizeResult.isPrivate());
        memorySlice.setSummary(summarizeResult.getSummary());
        memorySlice.setChatMessages(chatMessages);
        memorySlice.setStartUserId(interactionContext.getUserId());
        List<List<String>> relatedTopicPathList = new ArrayList<>();
        for (String string : summarizeResult.getRelatedTopicPath()) {
            List<String> list = Arrays.stream(string.split("->")).toList();
            relatedTopicPathList.add(list);
        }
        memorySlice.setRelatedTopics(relatedTopicPathList);
        return memorySlice;
    }
}
