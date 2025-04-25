package work.slhaf.agent.shared.memory;

import lombok.Builder;
import lombok.Data;
import work.slhaf.agent.common.chat.pojo.Message;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class EvaluatedSlice {
    private List<Message> chatMessages;
    private LocalDate date;
    private String summary;
}
