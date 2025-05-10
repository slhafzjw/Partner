package work.slhaf.agent.core.session;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.chat.pojo.MetaMessage;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.pojo.PersistableObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class SessionManager extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final String STORAGE_DIR = "./data/session/";

    private static SessionManager sessionManager;

    private String id;
    private HashMap<String /*startUserId*/, List<MetaMessage>> singleMetaMessageMap;
    private String currentMemoryId;
    private long lastUpdatedTime;

    public static SessionManager getInstance() throws IOException, ClassNotFoundException {
        if (sessionManager == null) {
            String id = Config.getConfig().getAgentId();
            Path filePath = Paths.get(STORAGE_DIR, id + ".session");
            if (Files.exists(filePath)) {
                sessionManager = deserialize(id);
            } else {
                sessionManager = new SessionManager();
                sessionManager.setSingleMetaMessageMap(new HashMap<>());
                sessionManager.id = id;
                sessionManager.setShutdownHook();
                sessionManager.lastUpdatedTime = 0;
            }
        }
        return sessionManager;
    }

    private void setShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                sessionManager.serialize();
                log.info("SessionManager 已保存");
            } catch (IOException e) {
                log.error("保存 SessionManager 失败: ", e);
            }
        }));
    }

    public void addMetaMessage(String userId, MetaMessage metaMessage) {
        if (singleMetaMessageMap.containsKey(userId)) {
            singleMetaMessageMap.get(userId).add(metaMessage);
        } else {
            singleMetaMessageMap.put(userId, new java.util.ArrayList<>());
            singleMetaMessageMap.get(userId).add(metaMessage);
        }
    }

    public List<Message> unpackAndClear(String userId) {
        List<Message> messages = new ArrayList<>();
        for (MetaMessage metaMessage : singleMetaMessageMap.get(userId)) {
            messages.add(metaMessage.getUserMessage());
            messages.add(metaMessage.getAssistantMessage());
        }
        singleMetaMessageMap.remove(userId);
        return messages;
    }

    public void refreshMemoryId() {
        currentMemoryId = UUID.randomUUID().toString();
    }

    public void serialize() throws IOException {
        Path filePath = Paths.get(STORAGE_DIR, this.id + ".session");
        Files.createDirectories(Path.of(STORAGE_DIR));
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(filePath.toFile()))) {
            oos.writeObject(this);
            log.info("SessionManager 已保存到: {}", filePath);
        } catch (IOException e) {
            log.error("序列化保存失败: {}", e.getMessage());
        }
    }

    private static SessionManager deserialize(String id) throws IOException, ClassNotFoundException {
        Path filePath = Paths.get(STORAGE_DIR, id + ".session");
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath.toFile()))) {
            SessionManager sessionManager = (SessionManager) ois.readObject();
            log.info("SessionManager 已从文件加载: {}", filePath);
            return sessionManager;
        }
    }

    public void resetLastUpdatedTime() {
        lastUpdatedTime = System.currentTimeMillis();
    }
}


