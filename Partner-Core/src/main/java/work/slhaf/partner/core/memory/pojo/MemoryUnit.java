package work.slhaf.partner.core.memory.pojo;

import lombok.Getter;
import work.slhaf.partner.framework.agent.model.pojo.Message;

import java.util.ArrayList;
import java.util.List;

@Getter
public class MemoryUnit {

    private final String id;
    private final List<Message> conversationMessages = new ArrayList<>();
    private Long timestamp;
    private final List<MemorySlice> slices = new ArrayList<>();

    public MemoryUnit(String id) {
        this.id = id;
    }

    public void updateTimestamp() {
        timestamp = System.currentTimeMillis();
    }
}
