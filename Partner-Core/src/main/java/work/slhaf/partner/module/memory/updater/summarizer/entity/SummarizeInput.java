package work.slhaf.partner.module.memory.updater.summarizer.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import work.slhaf.partner.api.agent.model.pojo.Message;

import java.util.List;

@AllArgsConstructor
@Data
public class SummarizeInput {
    private List<Message> chatMessages;
    private String topicTree;
}
