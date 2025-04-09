package work.slhaf.memory.content;

import lombok.Data;
import work.slhaf.memory.node.TopicNode;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;

@Data
public class MemorySlice implements Serializable {
    //关联的完整对话的id
    private String memoryId;
    //该切片在关联的完整对话中的顺序
    private Integer memoryRank;
    private String slicePath;
    private List<List<String>> relatedTopics;
    //关联完整对话中的前序切片, 排序为键，完整路径为值
    private LinkedHashMap<Integer,String> sliceBefore;
    private LinkedHashMap<Integer,String> sliceAfter;
}
