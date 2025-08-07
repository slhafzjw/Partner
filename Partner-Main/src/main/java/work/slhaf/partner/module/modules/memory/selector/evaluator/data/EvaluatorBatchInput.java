package work.slhaf.partner.module.modules.memory.selector.evaluator.data;

import lombok.Builder;
import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;

import java.util.List;

@Data
@Builder
public class EvaluatorBatchInput {
    private String text;
    private List<Message> history;
    private List<SliceSummary> memory_slices;
}
