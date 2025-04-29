package work.slhaf.agent.modules.memory.updater.summarizer;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.constant.ChatConstant;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;
import work.slhaf.agent.core.interaction.InteractionThreadPoolExecutor;
import work.slhaf.agent.modules.memory.updater.summarizer.data.SummarizeResult;
import work.slhaf.agent.modules.memory.updater.summarizer.data.TotalSummarizeInput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class MemorySummarizer extends Model {

    private static MemorySummarizer memorySummarizer;
    public static final String MODEL_KEY = "memory_summarizer";
    private static final List<String> prompts = List.of();

    private InteractionThreadPoolExecutor executor;

    public static MemorySummarizer getInstance() throws IOException, ClassNotFoundException {
        if (memorySummarizer == null) {
            memorySummarizer = new MemorySummarizer();
            memorySummarizer.setExecutor(InteractionThreadPoolExecutor.getInstance());
            setModel(Config.getConfig(), memorySummarizer, MODEL_KEY, ModelConstant.BASE_SUMMARIZER_PROMPT);
        }
        return memorySummarizer;
    }

    public SummarizeResult execute(TotalSummarizeInput input) throws InterruptedException {
            //进行长文本批量摘要
            singleMessageSummarize(input.getChatMessages());
            //进行整体摘要并返回结果
            return multiMessageSummarize(input);
    }

    private SummarizeResult multiMessageSummarize(TotalSummarizeInput input) {
        String messageStr = JSONUtil.toJsonPrettyStr(input);
        return multiSummarizeExecute(prompts.get(1),messageStr);
    }

    private SummarizeResult multiSummarizeExecute(String prompt, String messageStr) {
        ChatResponse response = chatClient.runChat(List.of(new Message(ChatConstant.Character.SYSTEM, prompt),
                new Message(ChatConstant.Character.USER, messageStr)));
        return JSONObject.parseObject(response.getMessage(), SummarizeResult.class);
    }

    private void singleMessageSummarize(List<Message> chatMessages) throws InterruptedException {
        List<Callable<Void>> tasks = new ArrayList<>();
        for (Message chatMessage : chatMessages) {
            if (chatMessage.getRole().equals(ChatConstant.Character.ASSISTANT)) {
                String content = chatMessage.getContent();
                if (chatMessage.getContent().length() > 500) {
                    tasks.add(() -> {
                        chatMessage.setContent(singleSummarizeExecute(prompts.get(0), content));
                        return null;
                    });
                }
            }
        }
        executor.invokeAll(tasks,30, TimeUnit.SECONDS);
    }

    private @NonNull String singleSummarizeExecute(String prompt, String content) {
        try {
            ChatResponse response = chatClient.runChat(List.of(new Message(ChatConstant.Character.SYSTEM, prompt),
                    new Message(ChatConstant.Character.USER, content)));
            return response.getMessage();
        } catch (Exception e) {
            log.error(e.getLocalizedMessage());
            return content;
        }
    }


}
