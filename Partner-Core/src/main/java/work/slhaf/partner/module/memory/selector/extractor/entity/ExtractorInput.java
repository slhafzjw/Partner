package work.slhaf.partner.module.memory.selector.extractor.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@AllArgsConstructor
public class ExtractorInput {
    private Map<LocalDateTime, String> inputs;
    private String topic_tree;
    private LocalDate date;
}
