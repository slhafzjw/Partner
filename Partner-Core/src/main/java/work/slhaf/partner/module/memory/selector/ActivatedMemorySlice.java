package work.slhaf.partner.module.memory.selector;

import lombok.Builder;
import lombok.Data;
import work.slhaf.partner.framework.agent.model.pojo.Message;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class ActivatedMemorySlice {
    private String unitId;
    private String sliceId;
    private LocalDate date;
    private Long timestamp;
    private String summary;
    private List<Message> messages;
}
