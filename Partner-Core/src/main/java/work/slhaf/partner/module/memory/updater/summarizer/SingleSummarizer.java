package work.slhaf.partner.module.memory.updater.summarizer;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

@EqualsAndHashCode(callSuper = true)
@Data
public class SingleSummarizer extends AbstractAgentModule.Sub<List<Message>, Void> implements ActivateModel {

    @InjectCapability
    private ActionCapability actionCapability;

    private ExecutorService executor;

    @Init
    public void init() {
        executor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
    }

    @Override
    public Void execute(List<Message> chatMessages) {
        log.debug("[MemorySummarizer] 长文本摘要开始...");
        CountDownLatch latch = new CountDownLatch(chatMessages.size());
        for (int i = 0; i < chatMessages.size(); i++) {
            Message chatMessage = chatMessages.get(i);
            if (chatMessage.getRole() == Message.Character.ASSISTANT) {
                String content = chatMessage.getContent();
                if (chatMessage.getContent().length() > 500) {
                    int index = i;
                    executor.execute(() -> {
                        try {
                            String summarized = singleExecute(JSONObject.of("content", content).toString());
                            chatMessages.set(index, new Message(Message.Character.ASSISTANT, summarized));
                        } finally {
                            latch.countDown();
                        }
                    });
                }
            }
        }
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
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
