package work.slhaf.partner.module.memory.updater.summarizer;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.abstracts.ActivateModel;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.module.memory.updater.summarizer.entity.SummarizeInput;
import work.slhaf.partner.module.memory.updater.summarizer.entity.SummarizeResult;

import java.util.ArrayList;
import java.util.List;

import static work.slhaf.partner.common.util.ExtractUtil.fixTopicPath;

@EqualsAndHashCode(callSuper = true)
@Data
public class MultiSummarizer extends AbstractAgentModule.Sub<SummarizeInput, SummarizeResult> implements ActivateModel {
    @Override
    public SummarizeResult execute(SummarizeInput input) {
        log.debug("[MemorySummarizer] 整体摘要开始...");
        SummarizeResult result = formattedChat(
                List.of(new Message(Message.Character.USER, JSONUtil.toJsonPrettyStr(input))),
                SummarizeResult.class
        );
        log.debug("[MemorySummarizer] 整体摘要结果: {}", JSONObject.toJSONString(result));
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
}
