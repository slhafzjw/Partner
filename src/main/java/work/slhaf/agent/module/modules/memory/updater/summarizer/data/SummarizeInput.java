package work.slhaf.agent.module.modules.memory.updater.summarizer.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import work.slhaf.agent.common.chat.pojo.Message;

import java.util.List;

@AllArgsConstructor
@Data
public class SummarizeInput {
    private List<Message> chatMessages;
    private String topicTree;
}
