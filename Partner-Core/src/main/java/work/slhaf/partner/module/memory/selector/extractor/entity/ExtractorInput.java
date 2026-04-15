package work.slhaf.partner.module.memory.selector.extractor.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import work.slhaf.partner.framework.agent.interaction.flow.RunningFlowContext;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
public class ExtractorInput {
    private List<RunningFlowContext.InputEntry> inputs;
    private String topic_tree;
    private LocalDate date;
}
