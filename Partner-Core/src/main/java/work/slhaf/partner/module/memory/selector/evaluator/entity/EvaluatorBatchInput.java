package work.slhaf.partner.module.memory.selector.evaluator.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import work.slhaf.partner.module.memory.selector.ActivatedMemorySlice;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@AllArgsConstructor
public class EvaluatorBatchInput {
    private Map<LocalDateTime, String> inputs;
    private ActivatedMemorySlice activatedMemorySlice;
}
