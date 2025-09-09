package work.slhaf.partner.core.submodule.memory.pojo.node;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.common.entity.PersistableObject;

import java.io.Serial;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@EqualsAndHashCode(callSuper = true)
@Data
public class TopicNode extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private ConcurrentHashMap<String,TopicNode> topicNodes = new ConcurrentHashMap<>();
    private CopyOnWriteArrayList<MemoryNode> memoryNodes = new CopyOnWriteArrayList<>();
}
