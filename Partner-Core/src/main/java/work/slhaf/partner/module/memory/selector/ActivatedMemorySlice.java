package work.slhaf.partner.module.memory.selector;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.framework.agent.common.entity.PersistableObject;
import work.slhaf.partner.framework.agent.model.pojo.Message;

import java.io.Serial;
import java.time.LocalDate;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
public class ActivatedMemorySlice extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private String unitId;
    private String sliceId;
    private LocalDate date;
    private Long timestamp;
    private String summary;
    private List<Message> messages;
}
