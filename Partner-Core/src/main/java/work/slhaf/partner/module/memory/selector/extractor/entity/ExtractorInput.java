package work.slhaf.partner.module.memory.selector.extractor.entity;

import lombok.Builder;
import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.memory.pojo.ActivatedMemorySlice;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class ExtractorInput {
    private String text;
    private String topic_tree;
    private LocalDate date;
    private List<Message> history;
    private List<ActivatedMemorySlice> activatedMemorySlices;
}
