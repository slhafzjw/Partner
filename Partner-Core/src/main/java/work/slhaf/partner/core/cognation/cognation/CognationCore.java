package work.slhaf.partner.core.cognation.cognation;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import work.slhaf.partner.common.chat.pojo.Message;
import work.slhaf.partner.common.serialize.PersistableObject;
import work.slhaf.partner.core.cognation.submodule.cache.CacheCore;
import work.slhaf.partner.core.cognation.submodule.memory.MemoryCore;
import work.slhaf.partner.core.cognation.submodule.perceive.PerceiveCore;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class CognationCore extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String STORAGE_DIR = "./data/memory/";
    private static volatile CognationCore cognationCore;

    private String id;
    private MemoryCore memoryCore = new MemoryCore();
    private CacheCore cacheCore = new CacheCore();
    private PerceiveCore perceiveCore = new PerceiveCore();

    /**
     * 主模型的聊天记录
     */
    private List<Message> chatMessages = new ArrayList<>();

    public CognationCore(String id) {
        this.id = id;
    }

    public static CognationCore getInstance(String id) throws IOException, ClassNotFoundException {
        if (cognationCore == null) {
            synchronized (CognationCore.class) {
                // 检查存储目录是否存在，不存在则创建
                if (cognationCore == null) {
                    createStorageDirectory();
                    Path filePath = getFilePath(id);
                    if (Files.exists(filePath)) {
                        cognationCore = deserialize(id);
                    } else {
                        FileUtils.createParentDirectories(filePath.toFile().getParentFile());
                        cognationCore = new CognationCore(id);
                        cognationCore.serialize();
                    }
                    log.info("CognationCore注册完毕...");
                }
            }
        }
        return cognationCore;
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
            log.info("CognationCore 已保存到: {}", path);
        } catch (IOException e) {
            Files.delete(filePath);
            log.error("序列化保存失败: {}", e.getMessage());
        }
    }

    private static CognationCore deserialize(String id) throws IOException, ClassNotFoundException {
        Path filePath = getFilePath(id);
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(filePath.toFile()))) {
            CognationCore graph = (CognationCore) ois.readObject();
            log.info("CognationCore 已从文件加载: {}", filePath);
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

