package work.slhaf.partner.module.memory.selector.evaluator.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import work.slhaf.partner.framework.agent.interaction.flow.RunningFlowContext;
import work.slhaf.partner.module.memory.selector.ActivatedMemorySlice;

import java.util.List;

@Data
@AllArgsConstructor
public class EvaluatorBatchInput {
    private List<RunningFlowContext.InputEntry> inputs;
    private ActivatedMemorySlice activatedMemorySlice;
}
