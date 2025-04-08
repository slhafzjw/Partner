package work.slhaf.memory.content;

import lombok.Data;
import work.slhaf.memory.node.TopicNode;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;

@Data
public class MemorySlice implements Serializable {
    private String memoryId;
    private Integer memoryRank;
    private String slicePath;
    private List<TopicNode> relatedTopics;
    private LinkedHashMap<Integer,String> sliceBefore;
    private LinkedHashMap<Integer,String> sliceAfter;
}
