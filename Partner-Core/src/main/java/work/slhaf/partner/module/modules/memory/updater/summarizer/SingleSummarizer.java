package work.slhaf.partner.module.modules.memory.updater.summarizer;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.factory.component.annotation.Init;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.common.thread.InteractionThreadPoolExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@EqualsAndHashCode(callSuper = true)
@Data
public class SingleSummarizer extends AbstractAgentModule.Sub<List<Message>, Void> implements ActivateModel {
    private InteractionThreadPoolExecutor executor;

    @Init
    public void init() {
        this.executor = InteractionThreadPoolExecutor.getInstance();
    }

    @Override
    public Void execute(List<Message> chatMessages) {
        log.debug("[MemorySummarizer] 长文本摘要开始...");
        List<Callable<Void>> tasks = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger();
        for (int i = 0; i < chatMessages.size(); i++) {
            Message chatMessage = chatMessages.get(i);
            if (chatMessage.getRole() == Message.Character.ASSISTANT) {
                String content = chatMessage.getContent();
                if (chatMessage.getContent().length() > 500) {
                    int index = i;
                    tasks.add(() -> {
                        int thisCount = counter.incrementAndGet();
                        log.debug("[MemorySummarizer] 长文本摘要[{}]启动", thisCount);
                        String summarized = singleExecute(JSONObject.of("content", content).toString());
                        chatMessages.set(index, new Message(Message.Character.ASSISTANT, summarized));
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
            return chat(List.of(new Message(Message.Character.USER, primaryContent)));
        } catch (Exception e) {
            log.error("[SingleSummarizer] 单消息总结出错: ", e);
            return primaryContent;
        }
    }

    @Override
    public String modelKey() {
        return "single_summarizer";
    }
}
