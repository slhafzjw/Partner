package work.slhaf.partner.module.memory.selector.extractor.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import work.slhaf.partner.module.memory.selector.MemoryInputEntry;

import java.util.List;

@Data
@AllArgsConstructor
public class ExtractorInput {
    private List<MemoryInputEntry> memoryInputEntries;
    private String topic_tree;
}
