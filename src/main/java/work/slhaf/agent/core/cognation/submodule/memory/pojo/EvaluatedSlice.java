package work.slhaf.agent.core.cognation.submodule.memory.pojo;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.serialize.PersistableObject;

import java.io.Serial;
import java.time.LocalDate;

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
