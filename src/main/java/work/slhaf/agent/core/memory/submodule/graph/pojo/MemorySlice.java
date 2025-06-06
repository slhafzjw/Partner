package work.slhaf.agent.core.memory.submodule.graph.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.serialize.PersistableObject;

import java.io.Serial;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemorySlice extends PersistableObject implements Comparable<MemorySlice> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关联的完整对话的id
     */
    private String memoryId;

    /**
     * 该切片在关联的完整对话中的顺序, 由时间戳确定
     */
    private Long timestamp;

    /**
     * 格式为"<日期>.slice", 如2025-04-11.slice
     */
    private String summary;

    private List<Message> chatMessages;

    /**
     * 关联的其他主题, 即"邻近节点(联系)"
     */
    private List<List<String>> relatedTopics;

    /**
     * 关联完整对话中的前序切片, 排序为键，完整路径为值
     */
    @ToString.Exclude
    private MemorySlice sliceBefore, sliceAfter;

    /**
     * 多用户设定
     * 发起该切片对话的用户
     */
    private String startUserId;

    /**
     * 该切片涉及到的用户uuid
     */
    private List<String> involvedUserIds;

    /**
     * 是否仅供发起用户作为记忆参考
     */
    private boolean isPrivate;

    /**
     * 摘要向量化结果
     */
    private float[] summaryEmbedding;

    /**
     * 是否向量化
     */
    private boolean embedded;

    @Override
    public int compareTo(MemorySlice memorySlice) {
        if (memorySlice.getTimestamp() > this.getTimestamp()) {
            return -1;
        } else if (memorySlice.getTimestamp() < this.timestamp) {
            return 1;
        }
        return 0;
    }

}
