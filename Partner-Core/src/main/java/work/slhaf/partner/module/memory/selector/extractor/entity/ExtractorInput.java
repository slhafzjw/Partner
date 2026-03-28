package work.slhaf.partner.module.memory.selector.extractor.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class ExtractorInput {
    private String input;
    private String topic_tree;
    private LocalDate date;
}
