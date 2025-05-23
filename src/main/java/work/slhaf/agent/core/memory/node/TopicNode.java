package work.slhaf.agent.core.memory.node;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.pojo.PersistableObject;

import java.io.Serial;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@EqualsAndHashCode(callSuper = true)
@Data
public class TopicNode extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private ConcurrentHashMap<String,TopicNode> topicNodes;
    private CopyOnWriteArrayList<MemoryNode> memoryNodes;
}
