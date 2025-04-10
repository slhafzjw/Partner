package work.slhaf.memory.content;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class MemorySlice implements Serializable, Comparable<MemorySlice> {
    //关联的完整对话的id
    private String memoryId;
    //该切片在关联的完整对话中的顺序, 由时间戳确定
    private Long timestamp;
    private String slicePath;
    private List<List<String>> relatedTopics;
    //关联完整对话中的前序切片, 排序为键，完整路径为值
    private MemorySlice sliceBefore;
    private MemorySlice sliceAfter;

    @Override
    public int compareTo(MemorySlice memorySlice) {
        if (memorySlice.getTimestamp() > this.getTimestamp()) {
            return -1;
        } else if (memorySlice.getTimestamp() < this.timestamp) {
            return 1;
        }
        return 0;
    }

    public SliceData getSliceData(){
        //todo: 待实现获取逻辑
        return new SliceData();
    }

    public void saveSlice(SliceData sliceData){
        //todo: 待实现存储逻辑, 该逻辑内将设置`slicePath`
    }
}
