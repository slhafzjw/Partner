package work.slhaf.partner.module.modules.memory.updater.summarizer;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.common.chat.pojo.ChatResponse;
import work.slhaf.partner.api.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.flow.abstracts.AgentInteractionSubModule;
import work.slhaf.partner.module.common.model.ModelConstant;
import work.slhaf.partner.module.modules.memory.updater.summarizer.data.SummarizeInput;
import work.slhaf.partner.module.modules.memory.updater.summarizer.data.SummarizeResult;

import java.util.ArrayList;
import java.util.List;

import static work.slhaf.partner.common.util.ExtractUtil.extractJson;
import static work.slhaf.partner.common.util.ExtractUtil.fixTopicPath;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class MultiSummarizer extends AgentInteractionSubModule<SummarizeInput, SummarizeResult> implements ActivateModel {

    private static volatile MultiSummarizer multiSummarizer;

    private MultiSummarizer() {
        modelSettings();
    }

    public static MultiSummarizer getInstance() {
        if (multiSummarizer == null) {
            synchronized (MultiSummarizer.class) {
                if (multiSummarizer == null) {
                    multiSummarizer = new MultiSummarizer();
                    multiSummarizer.updateChatClientSettings();
                }
            }
        }
        return multiSummarizer;
    }

    @Override
    public SummarizeResult execute(SummarizeInput input) {
        log.debug("[MemorySummarizer] 整体摘要开始...");
        ChatResponse response = this.singleChat(JSONUtil.toJsonPrettyStr(input));
        log.debug("[MemorySummarizer] 整体摘要结果: {}", JSONObject.toJSONString(response));
        SummarizeResult result = JSONObject.parseObject(extractJson(response.getMessage()), SummarizeResult.class);
        return fix(result);
    }

    private SummarizeResult fix(SummarizeResult result) {
        if (result == null || result.getTopicPath() == null || result.getTopicPath().isEmpty()) {
            return result;
        }

        String topicPath = fixTopicPath(result.getTopicPath());
        List<String> relatedTopicPath = new ArrayList<>();
        for (String s : result.getRelatedTopicPath()) {
            relatedTopicPath.add(fixTopicPath(s));
        }
        result.setTopicPath(topicPath);
        result.setRelatedTopicPath(relatedTopicPath);
        return result;
    }

    @Override
    public String modelKey() {
        return "multi_summarizer";
    }

    @Override
    public boolean withBasicPrompt() {
        return true;
    }

    @Override
    public String promptModule() {
        return ModelConstant.Prompt.MEMORY;
    }
}
