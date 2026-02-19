package work.slhaf.partner.core.memory.pojo.node;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.common.entity.PersistableObject;
import work.slhaf.partner.core.memory.exception.NullSliceListException;
import work.slhaf.partner.core.memory.pojo.MemorySlice;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class MemoryNode extends PersistableObject implements Comparable<MemoryNode> {

    @Serial
    private static final long serialVersionUID = 1L;

    private static String SLICE_DATA_DIR = "./data/memory/slice/";

    /**
     * 记忆节点唯一标识, 用于作为实际文件名, 如(xxxx-xxxxx-xxxxx.slice)
     */
    private String memoryNodeId;

    /**
     * 记忆节点所属日期
     */
    private LocalDate localDate;

    /**
     * 该日期对应的全部记忆切片
     */
    private CopyOnWriteArrayList<MemorySlice> memorySliceList;

    @Override
    public int compareTo(MemoryNode memoryNode) {
        if (memoryNode.getLocalDate().isAfter(this.localDate)) {
            return -1;
        } else if (memoryNode.getLocalDate().isBefore(this.localDate)) {
            return 1;
        }
        return 0;
    }

    public List<MemorySlice> loadMemorySliceList() throws IOException, ClassNotFoundException {
        //检查是否存在对应文件
        File file = new File(SLICE_DATA_DIR+this.getMemoryNodeId()+".slice");
        if (file.exists()){
            this.memorySliceList = deserialize(file);
        }else {
            //逻辑正常的话，这部分应该不会出现，除非在insertMemory中进行save操作之前出现异常，中断了方法，但程序却没有结束
            this.memorySliceList = new CopyOnWriteArrayList<>();
        }
        return this.memorySliceList;
    }

    public void saveMemorySliceList() throws IOException {
        if (memorySliceList == null){
            throw new NullSliceListException("memorySliceList为NULL! 检查实现逻辑!");
        }
        File file = new File(SLICE_DATA_DIR+this.getMemoryNodeId()+".slice");
        Files.createDirectories(Path.of(SLICE_DATA_DIR));
        try(ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))){
            oos.writeObject(this.memorySliceList);
        }
        //取消切片挂载, 释放内存
        this.memorySliceList = null;
    }

    private CopyOnWriteArrayList<MemorySlice> deserialize(File file) throws IOException, ClassNotFoundException {
        try(ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (CopyOnWriteArrayList<MemorySlice>) ois.readObject();
        }
    }
}
