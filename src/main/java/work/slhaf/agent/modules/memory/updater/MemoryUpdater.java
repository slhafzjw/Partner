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
import work.slhaf.agent.modules.memory.selector.extractor.MemorySelectExtractor;
import work.slhaf.agent.modules.memory.updater.summarizer.MemorySummarizer;
import work.slhaf.agent.modules.memory.updater.summarizer.data.SummarizeResult;

import java.io.IOException;
import java.util.List;

@Data
@Slf4j
public class MemoryUpdater implements InteractionModule {

    private static MemoryUpdater memoryUpdater;

    private MemoryManager memoryManager;
    private InteractionThreadPoolExecutor executor;
    private MemorySelectExtractor memorySelectExtractor;
    private MemorySummarizer memorySummarizer;

    private MemoryUpdater() {
    }

    public static MemoryUpdater getInstance() throws IOException, ClassNotFoundException {
        if (memoryUpdater == null) {
            memoryUpdater = new MemoryUpdater();
            memoryUpdater.setMemoryManager(MemoryManager.getInstance());
            memoryUpdater.setMemorySelectExtractor(MemorySelectExtractor.getInstance());
            memoryUpdater.setMemorySummarizer(MemorySummarizer.getInstance());
        }
        return memoryUpdater;
    }

    @Override
    public void execute(InteractionContext interactionContext) {
        if (interactionContext.isFinished()) {
            return;
        }
        //如果token 大于阈值，则更新记忆
        JSONObject moduleContext = interactionContext.getModuleContext();
        if (moduleContext.getIntValue("total_token") > 24000 || (moduleContext.containsKey("new_topic") && moduleContext.getBooleanValue("new_topic"))) {
            executor.execute(() -> {
                //整理切片
                List<Message> chatMessages = moduleContext.getList("messages_to_store", Message.class);
                //进行摘要、判断是否为私密记忆、生成主题路径
                try {
                    SummarizeResult summarizeResult = memorySummarizer.execute(chatMessages);
                    //整理为切片并存储
                    MemorySlice memorySlice = new MemorySlice();

//                memoryManager.insertSlice();
                } catch (Exception e) {
                    log.error("记忆更新出错: {}", e.getLocalizedMessage());
                }
            });
        }

        //更新确定性记忆
        executor.execute(() -> {

        });

    }
}
