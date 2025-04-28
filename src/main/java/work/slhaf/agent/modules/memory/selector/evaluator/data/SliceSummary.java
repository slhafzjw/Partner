package work.slhaf.agent.modules.memory.selector.evaluator.data;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SliceSummary {
    private String summary;
    private Long id;
    private LocalDate date;
}
