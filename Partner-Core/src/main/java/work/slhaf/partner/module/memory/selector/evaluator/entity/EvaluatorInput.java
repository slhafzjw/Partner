package work.slhaf.partner.module.memory.selector.evaluator.entity;

import lombok.Builder;
import lombok.Data;
import work.slhaf.partner.core.memory.pojo.ActivatedMemorySlice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class EvaluatorInput {
    private Map<LocalDateTime, String> inputs;
    private List<ActivatedMemorySlice> memorySlices;
}
