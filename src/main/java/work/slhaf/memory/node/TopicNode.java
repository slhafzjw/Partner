package work.slhaf.memory.node;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

@Data
public class TopicNode implements Serializable {
    private HashMap<String,TopicNode> topicNodes;
    private List<MemoryNode> memoryNodes;
}
