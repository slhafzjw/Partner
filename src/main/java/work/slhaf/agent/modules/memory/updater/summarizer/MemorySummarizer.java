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
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;
import work.slhaf.agent.core.interaction.InteractionThreadPoolExecutor;
import work.slhaf.agent.modules.memory.updater.summarizer.data.SummarizeInput;
import work.slhaf.agent.modules.memory.updater.summarizer.data.SummarizeResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static work.slhaf.agent.common.util.ExtractUtil.extractJson;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class MemorySummarizer extends Model {

    private static MemorySummarizer memorySummarizer;
    public static final String MODEL_KEY = "memory_summarizer";
    private static final List<String> prompts = List.of(
            Constant.SINGLE_SUMMARIZE_PROMPT,
            Constant.MULTI_SUMMARIZE_PROMPT,
            Constant.TOTAL_SUMMARIZE_PROMPT
    );

    private InteractionThreadPoolExecutor executor;

    public static MemorySummarizer getInstance() throws IOException, ClassNotFoundException {
        if (memorySummarizer == null) {
            memorySummarizer = new MemorySummarizer();
            memorySummarizer.setExecutor(InteractionThreadPoolExecutor.getInstance());
            setModel(memorySummarizer, MODEL_KEY, ModelConstant.Prompt.MEMORY,true);
        }
        return memorySummarizer;
    }

    public SummarizeResult execute(SummarizeInput input) throws InterruptedException {
        //进行长文本批量摘要
        singleMessageSummarize(input.getChatMessages());
        //进行整体摘要并返回结果
        return multiMessageSummarize(input);
    }

    private SummarizeResult multiMessageSummarize(SummarizeInput input) {
        String messageStr = JSONUtil.toJsonPrettyStr(input);
        return multiSummarizeExecute(prompts.get(1), messageStr);
    }

    private SummarizeResult multiSummarizeExecute(String prompt, String messageStr) {
        log.debug("[MemorySummarizer] 整体摘要开始...");
        ChatResponse response = chatClient.runChat(List.of(new Message(ChatConstant.Character.SYSTEM, prompt),
                new Message(ChatConstant.Character.USER, messageStr)));
        log.debug("[MemorySummarizer] 整体摘要结果: {}",response);
        return JSONObject.parseObject(extractJson(response.getMessage()), SummarizeResult.class);
    }

    private void singleMessageSummarize(List<Message> chatMessages) {
        log.debug("[MemorySummarizer] 长文本摘要开始...");
        List<Callable<Void>> tasks = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger();
        for (Message chatMessage : chatMessages) {
            if (chatMessage.getRole().equals(ChatConstant.Character.ASSISTANT)) {
                String content = chatMessage.getContent();
                if (chatMessage.getContent().length() > 500) {
                    tasks.add(() -> {
                        int thisCount = counter.incrementAndGet();
                        log.debug("[MemorySummarizer] 长文本摘要[{}]启动",thisCount);
                        chatMessage.setContent(singleSummarizeExecute(prompts.getFirst(), JSONObject.of("content", content).toString()));
                        log.debug("[MemorySummarizer] 长文本摘要[{}]完成",thisCount);
                        return null;
                    });
                }
            }
        }
        executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
        log.debug("[MemorySummarizer] 长文本摘要结束");
    }

    private @NonNull String singleSummarizeExecute(String prompt, String content) {
        try {
            ChatResponse response = chatClient.runChat(List.of(new Message(ChatConstant.Character.SYSTEM, prompt),
                    new Message(ChatConstant.Character.USER, content)));
            return JSONObject.parseObject(extractJson(response.getMessage())).getString("content");
        } catch (Exception e) {
            log.error(e.getLocalizedMessage());
            return content;
        }
    }


    public String executeTotalSummary(HashMap<String, String> singleMemorySummary) {
        ChatResponse response = chatClient.runChat(List.of(new Message(ChatConstant.Character.SYSTEM, prompts.get(2)),
                new Message(ChatConstant.Character.USER, JSONUtil.toJsonPrettyStr(singleMemorySummary))));
        return JSONObject.parseObject(extractJson(response.getMessage())).getString("content");
    }

    private static class Constant {
        public static final String SINGLE_SUMMARIZE_PROMPT = "";

        public static final String MULTI_SUMMARIZE_PROMPT = "";

        public static final String TOTAL_SUMMARIZE_PROMPT = "";
    }
}
