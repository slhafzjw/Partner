package work.slhaf.agent.shared.memory;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.pojo.PersistableObject;

import java.io.Serial;
import java.time.LocalDate;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
public class EvaluatedSlice extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

//    private List<Message> chatMessages;
    private LocalDate date;
    private String summary;
}
