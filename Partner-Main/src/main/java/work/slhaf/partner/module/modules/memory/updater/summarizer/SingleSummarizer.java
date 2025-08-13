package work.slhaf.partner.module.modules.memory.updater.summarizer;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.api.chat.constant.ChatConstant;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.common.thread.InteractionThreadPoolExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class SingleSummarizer extends AgentRunningSubModule<List<Message>,Void> implements ActivateModel {

    private static volatile SingleSummarizer singleSummarizer;

    private InteractionThreadPoolExecutor executor;


    private SingleSummarizer() {
        modelSettings();
    }
    public static SingleSummarizer getInstance() {
        if (singleSummarizer == null) {
            synchronized (SingleSummarizer.class) {
                if (singleSummarizer == null) {
                    singleSummarizer = new SingleSummarizer();
                    singleSummarizer.setExecutor(InteractionThreadPoolExecutor.getInstance());
                }
            }
        }
        return singleSummarizer;
    }

    @Override
    public Void execute(List<Message> chatMessages) {
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
        return null;
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
    public String modelKey() {
        return "single_summarizer";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }

}
