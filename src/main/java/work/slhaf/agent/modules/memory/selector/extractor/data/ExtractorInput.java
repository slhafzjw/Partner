package work.slhaf.agent.modules.memory.selector.extractor.data;

import lombok.Builder;
import lombok.Data;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.shared.memory.EvaluatedSlice;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class ExtractorInput {
    private String text;
    private String topic_tree;
    private LocalDate date;
    private List<Message> history;
    private List<EvaluatedSlice> activatedMemorySlices;
}
