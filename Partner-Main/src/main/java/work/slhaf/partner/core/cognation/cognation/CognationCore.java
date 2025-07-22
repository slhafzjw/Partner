package work.slhaf.partner.core.cognation.cognation;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import work.slhaf.partner.api.capability.annotation.CapabilityCore;
import work.slhaf.partner.common.chat.pojo.Message;
import work.slhaf.partner.common.serialize.PersistableObject;
import work.slhaf.partner.core.cognation.cognation.pojo.ActiveData;
import work.slhaf.partner.core.cognation.submodule.cache.CacheCore;
import work.slhaf.partner.core.cognation.submodule.memory.MemoryCore;
import work.slhaf.partner.core.cognation.submodule.memory.pojo.EvaluatedSlice;
import work.slhaf.partner.core.cognation.submodule.perceive.PerceiveCore;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
@CapabilityCore(value = "cognation")
public class CognationCore extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String STORAGE_DIR = "./data/memory/";
    private static volatile CognationCore cognationCore;

    private MemoryCore memoryCore = new MemoryCore();
    private CacheCore cacheCore = new CacheCore();
    private PerceiveCore perceiveCore = new PerceiveCore();
    private ReentrantLock messageLock = new ReentrantLock();
    private ActiveData activeData;

    /**
     * 主模型的聊天记录
     */
    private List<Message> chatMessages = new ArrayList<>();

    public CognationCore() throws IOException, ClassNotFoundException {
        createStorageDirectory();
        Path filePath = getFilePath("partner");
        if (Files.exists(filePath)) {
            setupData(this);
        } else {
            FileUtils.createParentDirectories(filePath.toFile().getParentFile());
            connectCores(this);
            this.serialize();
        }
        setupHook(this);
        log.info("CognationCore注册完毕...");
    }

    private void connectCores(CognationCore temp) {
        temp.setCacheCore(CacheCore.getInstance());
        temp.setMemoryCore(MemoryCore.getInstance());
        temp.setPerceiveCore(PerceiveCore.getInstance());
    }

    private void setupHook(CognationCore temp) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                temp.serialize();
                log.info("[CognationCore] CognationCore已保存");
            } catch (IOException e) {
                log.error("[CognationCore] CognationCore保存失败: ", e);
            }
        }));

    }

    private void setupData(CognationCore temp) throws IOException, ClassNotFoundException {
        CognationCore deserialize = deserialize();
        temp.activeData = deserialize.activeData;
        temp.memoryCore = deserialize.memoryCore;
        temp.cacheCore = deserialize.cacheCore;
        temp.perceiveCore = deserialize.perceiveCore;
        temp.chatMessages = deserialize.chatMessages;
    }


    public static CognationCore getInstance() {
        return cognationCore;
    }

    public void serialize() throws IOException {
        //先写入到临时文件，如果正常写入则覆盖原文件
        Path filePath = getFilePath("partner-temp");
        Files.createDirectories(Path.of(STORAGE_DIR));
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath.toFile()));
            oos.writeObject(this);
            oos.close();
            Path path = getFilePath("partner");
            Files.move(filePath, path, StandardCopyOption.REPLACE_EXISTING);
            log.info("CognationCore 已保存到: {}", path);
        } catch (IOException e) {
            Files.delete(filePath);
            log.error("序列化保存失败: {}", e.getMessage());
        }
    }

    private static CognationCore deserialize() throws IOException, ClassNotFoundException {
        Path filePath = getFilePath("partner");
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(filePath.toFile()))) {
            CognationCore graph = (CognationCore) ois.readObject();
            log.info("CognationCore 已从文件加载: {}", filePath);
            return graph;
        }
    }

    private static Path getFilePath(String s) {
        return Paths.get(STORAGE_DIR, s + ".memory");
    }

    private static void createStorageDirectory() {
        try {
            Files.createDirectories(Paths.get(STORAGE_DIR));
        } catch (IOException e) {
            System.err.println("创建存储目录失败: " + e.getMessage());
        }
    }

    public void cleanMessage(List<Message> messages) {
        messageLock.lock();
        this.getChatMessages().removeAll(messages);
        messageLock.unlock();

    }

    public void updateActivatedSlices(String userId, List<EvaluatedSlice> memorySlices) {
        activeData.updateActivatedSlices(userId, memorySlices);
        log.debug("[CoordinatedManager] 已更新激活切片, userId: {}", userId);
    }

    public String getActivatedSlicesStr(String userId) {
        return activeData.getActivatedSlicesStr(userId);
    }

    public HashMap<String, List<EvaluatedSlice>> getActivatedSlices() {
        return activeData.getActivatedSlices();
    }

    public void clearActivatedSlices(String userId) {
        activeData.clearActivatedSlices(userId);
    }

    public boolean hasActivatedSlices(String userId) {
        return activeData.hasActivatedSlices(userId);
    }

    public int getActivatedSlicesSize(String userId) {
        return activeData.getActivatedSlices().get(userId).size();
    }

    public List<EvaluatedSlice> getActivatedSlices(String userId) {
        return activeData.getActivatedSlices().get(userId);
    }
}

