package work.slhaf.agent.core.memory;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.serialize.PersistableObject;
import work.slhaf.agent.core.memory.submodule.cache.CacheCore;
import work.slhaf.agent.core.memory.submodule.graph.GraphCore;
import work.slhaf.agent.core.memory.submodule.perceive.PerceiveCore;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class MemoryCore extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String STORAGE_DIR = "./data/memory/";
    private static volatile MemoryCore memoryCore;

    private String id;
    private GraphCore graphCore = new GraphCore();
    private CacheCore cacheCore = new CacheCore();
    private PerceiveCore perceiveCore = new PerceiveCore();

    /**
     * 主模型的聊天记录
     */
    private List<Message> chatMessages;

    public MemoryCore(String id) {
        this.id = id;
    }

    public static MemoryCore getInstance(String id) throws IOException, ClassNotFoundException {
        if (memoryCore == null) {
            synchronized (MemoryCore.class) {
                // 检查存储目录是否存在，不存在则创建
                if (memoryCore == null) {
                    createStorageDirectory();
                    Path filePath = getFilePath(id);
                    if (Files.exists(filePath)) {
                        memoryCore = deserialize(id);
                    } else {
                        FileUtils.createParentDirectories(filePath.toFile().getParentFile());
                        memoryCore = new MemoryCore(id);
                        memoryCore.serialize();
                    }
                    log.info("MemoryGraph注册完毕...");
                }
            }
        }
        return memoryCore;
    }

    public void serialize() throws IOException {
        //先写入到临时文件，如果正常写入则覆盖原文件
        Path filePath = getFilePath(this.id + "-temp");
        Files.createDirectories(Path.of(STORAGE_DIR));
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath.toFile()));
            oos.writeObject(this);
            oos.close();
            Path path = getFilePath(this.id);
            Files.move(filePath, path, StandardCopyOption.REPLACE_EXISTING);
            log.info("MemoryCore 已保存到: {}", path);
        } catch (IOException e) {
            Files.delete(filePath);
            log.error("序列化保存失败: {}", e.getMessage());
        }
    }

    private static MemoryCore deserialize(String id) throws IOException, ClassNotFoundException {
        Path filePath = getFilePath(id);
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(filePath.toFile()))) {
            MemoryCore graph = (MemoryCore) ois.readObject();
            log.info("MemoryCore 已从文件加载: {}", filePath);
            return graph;
        }
    }

    private static Path getFilePath(String id) {
        return Paths.get(STORAGE_DIR, id + ".memory");
    }

    private static void createStorageDirectory() {
        try {
            Files.createDirectories(Paths.get(STORAGE_DIR));
        } catch (IOException e) {
            System.err.println("创建存储目录失败: " + e.getMessage());
        }
    }

}

