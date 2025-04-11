package work.slhaf.memory.node;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.memory.content.MemorySlice;
import work.slhaf.memory.exception.NullSliceListException;

import java.io.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Slf4j
public class MemoryNode implements Serializable, Comparable<MemoryNode> {

    private static String SLICE_DATA_DIR = "./data/slice/";

    /**
     * 记忆节点所属日期, 以日期为文件名在硬盘存储记忆数据(如 2025-04-11.slice)
     */
    private LocalDate localDate;

    /**
     * 该日期对应的全部记忆切片
     */
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

    public List<MemorySlice> getMemorySliceList() throws IOException, ClassNotFoundException {
        //检查是否存在对应文件
        File file = new File(SLICE_DATA_DIR+this.getLocalDate()+".slice");
        if (file.exists()){
            this.memorySliceList = deserialize(file);
        }else {
            this.memorySliceList = new ArrayList<>();
        }
        return this.memorySliceList;
    }

    public void saveMemorySliceList() throws IOException {
        if (memorySliceList == null){
            throw new NullSliceListException("memorySliceList为NULL! 检查实现逻辑!");
        }
        File file = new File(SLICE_DATA_DIR+this.getLocalDate()+".slice");
        try(ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))){
            oos.writeObject(this.memorySliceList);
        }
        //取消切片挂载, 释放内存
        this.memorySliceList = null;
    }

    private List<MemorySlice> deserialize(File file) throws IOException, ClassNotFoundException {
        try(ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            List<MemorySlice> sliceList = (List<MemorySlice>) ois.readObject();
            log.info("读取记忆切片成功");
            return sliceList;
        }
    }
}
