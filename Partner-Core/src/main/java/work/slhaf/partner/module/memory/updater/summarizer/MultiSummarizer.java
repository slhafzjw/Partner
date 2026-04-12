package work.slhaf.partner.module.memory.updater.summarizer;

import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.memory.runtime.MemoryRuntime;
import work.slhaf.partner.module.memory.updater.summarizer.entity.SummarizeInput;
import work.slhaf.partner.module.memory.updater.summarizer.entity.SummarizeResult;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class MultiSummarizer extends AbstractAgentModule.Sub<SummarizeInput, Result<SummarizeResult>> implements ActivateModel {

    @InjectModule
    private MemoryRuntime memoryRuntime;

    @Override
    public @NotNull Result<SummarizeResult> execute(SummarizeInput input) {
        return formattedChat(
                List.of(new Message(Message.Character.USER, JSONUtil.toJsonPrettyStr(input))),
                SummarizeResult.class
        ).onSuccess(this::fix);
    }

    private void fix(SummarizeResult result) {
        if (result == null || result.getTopicPath() == null || result.getTopicPath().isEmpty()) {
            return;
        }
        String topicPath = memoryRuntime.fixTopicPath(result.getTopicPath());
        List<String> relatedTopicPath = new ArrayList<>();
        for (String s : result.getRelatedTopicPath()) {
            relatedTopicPath.add(memoryRuntime.fixTopicPath(s));
        }
        result.setTopicPath(topicPath);
        result.setRelatedTopicPath(relatedTopicPath);
    }

    @NotNull
    @Override
    public String modelKey() {
        return "multi_summarizer";
    }
}
