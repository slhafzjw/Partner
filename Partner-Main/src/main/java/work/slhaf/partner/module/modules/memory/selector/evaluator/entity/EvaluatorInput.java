package work.slhaf.partner.module.modules.memory.selector.evaluator.entity;

import lombok.Builder;
import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.memory.pojo.MemoryResult;

import java.util.List;

@Data
@Builder
public class EvaluatorInput {
    private String input;
    private List<Message> messages;
    private List<MemoryResult> memoryResults;
}
