package work.slhaf.agent.modules.memory.data.evaluator;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SliceSummary {
    private String summary;
    private Long id;
    private LocalDate date;
}
