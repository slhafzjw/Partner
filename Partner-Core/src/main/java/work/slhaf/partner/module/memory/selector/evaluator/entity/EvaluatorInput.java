package work.slhaf.partner.module.memory.selector.evaluator.entity;

import lombok.Builder;
import lombok.Data;
import work.slhaf.partner.framework.agent.interaction.flow.RunningFlowContext;
import work.slhaf.partner.module.memory.selector.ActivatedMemorySlice;

import java.util.List;

@Data
@Builder
public class EvaluatorInput {
    private List<RunningFlowContext.InputEntry> inputs;
    private List<ActivatedMemorySlice> memorySlices;
}
