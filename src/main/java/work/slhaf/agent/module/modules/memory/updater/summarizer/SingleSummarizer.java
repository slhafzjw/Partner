package work.slhaf.agent.module.modules.memory.updater.summarizer;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.constant.ChatConstant;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.thread.InteractionThreadPoolExecutor;
import work.slhaf.agent.module.common.Model;
import work.slhaf.agent.module.common.ModelConstant;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class SingleSummarizer extends Model {

    private static volatile SingleSummarizer singleSummarizer;

    private InteractionThreadPoolExecutor executor;

    public static SingleSummarizer getInstance() {
        if (singleSummarizer == null) {
            synchronized (SingleSummarizer.class) {
                if (singleSummarizer == null) {
                    singleSummarizer = new SingleSummarizer();
                    singleSummarizer.setExecutor(InteractionThreadPoolExecutor.getInstance());
                    setModel(singleSummarizer, singleSummarizer.modelKey(), ModelConstant.Prompt.MEMORY, false);
                }
            }
        }
        return singleSummarizer;
    }

    public void execute(List<Message> chatMessages) {
        log.debug("[MemorySummarizer] 长文本摘要开始...");
        List<Callable<Void>> tasks = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger();
        for (Message chatMessage : chatMessages) {
            if (chatMessage.getRole().equals(ChatConstant.Character.ASSISTANT)) {
                String content = chatMessage.getContent();
                if (chatMessage.getContent().length() > 500) {
                    tasks.add(() -> {
                        int thisCount = counter.incrementAndGet();
                        log.debug("[MemorySummarizer] 长文本摘要[{}]启动", thisCount);
                        chatMessage.setContent(singleExecute(JSONObject.of("content", content).toString()));
                        log.debug("[MemorySummarizer] 长文本摘要[{}]完成", thisCount);
                        return null;
                    });
                }
            }
        }
        executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
        log.debug("[MemorySummarizer] 长文本摘要结束");
    }

    private String singleExecute(String primaryContent) {
        try {
            ChatResponse response = this.singleChat(primaryContent);
            return response.getMessage();
        } catch (Exception e) {
            log.error("[SingleSummarizer] 单消息总结出错: ", e);
            return primaryContent;
        }
    }

    @Override
    protected String modelKey() {
        return "single_summarizer";
    }
}
