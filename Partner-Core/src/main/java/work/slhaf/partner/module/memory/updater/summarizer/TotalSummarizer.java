package work.slhaf.partner.module.memory.updater.summarizer;

import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;

import java.util.HashMap;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class TotalSummarizer extends AbstractAgentModule.Sub<HashMap<String, String>, String> implements ActivateModel {
    public String execute(HashMap<String, String> singleMemorySummary) {
        return formattedChat(
                List.of(new Message(Message.Character.USER, JSONUtil.toJsonPrettyStr(singleMemorySummary))),
                SummaryContent.class
        ).getContent();
    }

    @Override
    public String modelKey() {
        return "total_summarizer";
    }

    @lombok.Data
    private static class SummaryContent {
        private String content;
    }
}
