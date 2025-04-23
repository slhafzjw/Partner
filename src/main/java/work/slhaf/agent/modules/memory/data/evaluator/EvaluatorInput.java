package work.slhaf.agent.modules.memory.data.evaluator;

import lombok.Builder;
import lombok.Data;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.core.memory.pojo.MemoryResult;

import java.util.List;

@Data
@Builder
public class EvaluatorInput {
    private String input;
    private List<Message> messages;
    private List<MemoryResult> memoryResults;
}
