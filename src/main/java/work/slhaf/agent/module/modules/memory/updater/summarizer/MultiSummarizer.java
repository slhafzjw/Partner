package work.slhaf.agent.module.modules.memory.updater.summarizer;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.module.common.Model;
import work.slhaf.agent.module.common.ModelConstant;
import work.slhaf.agent.module.modules.memory.updater.summarizer.data.SummarizeInput;
import work.slhaf.agent.module.modules.memory.updater.summarizer.data.SummarizeResult;

import static work.slhaf.agent.common.util.ExtractUtil.extractJson;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class MultiSummarizer extends Model {

    public static final String MODEL_KEY = "multi_summarizer";
    private static volatile MultiSummarizer multiSummarizer;

    public static MultiSummarizer getInstance() {
        if (multiSummarizer == null) {
            synchronized (MultiSummarizer.class) {
                if (multiSummarizer == null) {
                    multiSummarizer = new MultiSummarizer();
                    setModel(multiSummarizer, MODEL_KEY, ModelConstant.Prompt.MEMORY, true);
                    multiSummarizer.updateChatClientSettings();
                }
            }
        }
        return multiSummarizer;
    }

    public SummarizeResult execute(SummarizeInput input) {
        log.debug("[MemorySummarizer] 整体摘要开始...");
        ChatResponse response = this.singleChat(JSONUtil.toJsonPrettyStr(input));
        log.debug("[MemorySummarizer] 整体摘要结果: {}", response);
        return JSONObject.parseObject(extractJson(response.getMessage()), SummarizeResult.class);
    }
}
