package work.slhaf.partner.module.modules.memory.selector.evaluator.entity;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SliceSummary {
    private String summary;
    private Long id;
    private LocalDate date;
}
