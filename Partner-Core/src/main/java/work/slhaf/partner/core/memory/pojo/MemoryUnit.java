package work.slhaf.partner.core.memory.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.framework.agent.common.entity.PersistableObject;
import work.slhaf.partner.framework.agent.model.pojo.Message;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemoryUnit extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private List<Message> conversationMessages = new ArrayList<>();
    private Long timestamp;
    private List<MemorySlice> slices = new ArrayList<>();
}
