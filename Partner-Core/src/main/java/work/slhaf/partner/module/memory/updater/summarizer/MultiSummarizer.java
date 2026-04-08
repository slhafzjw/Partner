package work.slhaf.partner.module.memory.updater.summarizer;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.module.memory.runtime.MemoryRuntime;
import work.slhaf.partner.module.memory.updater.summarizer.entity.SummarizeInput;
import work.slhaf.partner.module.memory.updater.summarizer.entity.SummarizeResult;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class MultiSummarizer extends AbstractAgentModule.Sub<SummarizeInput, SummarizeResult> implements ActivateModel {

    @InjectModule
    private MemoryRuntime memoryRuntime;

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
        String topicPath = memoryRuntime.fixTopicPath(result.getTopicPath());
        List<String> relatedTopicPath = new ArrayList<>();
        for (String s : result.getRelatedTopicPath()) {
            relatedTopicPath.add(memoryRuntime.fixTopicPath(s));
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
