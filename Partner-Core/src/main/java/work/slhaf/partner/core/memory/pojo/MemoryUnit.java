package work.slhaf.partner.core.memory.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.model.pojo.Message;
import work.slhaf.partner.api.common.entity.PersistableObject;

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
