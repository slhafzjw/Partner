package work.slhaf.agent.modules.memory.data.evaluator;

import lombok.Builder;
import lombok.Data;
import work.slhaf.agent.common.chat.pojo.Message;

import java.util.List;

@Data
@Builder
public class EvaluatorBatchInput {
    private String text;
    private List<Message> history;
    private List<SliceSummary> memory_slices;
}
