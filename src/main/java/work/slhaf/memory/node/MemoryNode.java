package work.slhaf.memory.node;

import lombok.Data;
import work.slhaf.memory.content.MemorySlice;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

@Data
public class MemoryNode implements Serializable, Comparable<MemoryNode> {
    //记忆节点所属日期
    private LocalDate localDate;
    //该日期对应的全部记忆切片
    private List<MemorySlice> memorySliceList;

    @Override
    public int compareTo(MemoryNode memoryNode) {
        if (memoryNode.getLocalDate().isAfter(this.localDate)) {
            return -1;
        } else if (memoryNode.getLocalDate().isBefore(this.localDate)) {
            return 1;
        }
        return 0;
    }
}
