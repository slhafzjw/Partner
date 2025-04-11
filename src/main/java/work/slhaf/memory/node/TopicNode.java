package work.slhaf.memory.node;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.memory.pojo.PersistableObject;

import java.io.Serial;
import java.util.HashMap;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class TopicNode extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private HashMap<String,TopicNode> topicNodes;
    private List<MemoryNode> memoryNodes;
}
