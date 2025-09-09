package work.slhaf.partner.runtime.session;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigManager;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.api.chat.pojo.MetaMessage;
import work.slhaf.partner.api.common.entity.PersistableObject;
import work.slhaf.partner.common.config.PartnerAgentConfigManager;
import work.slhaf.partner.common.exception.ServiceLoadFailedException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

    private static volatile SessionManager sessionManager;

    private String id;
    private HashMap<String /*startUserId*/, List<MetaMessage>> singleMetaMessageMap;
    private String currentMemoryId;
    private long lastUpdatedTime;

    public static SessionManager getInstance() {
        if (sessionManager == null) {
            synchronized (SessionManager.class) {
                if (sessionManager == null) {
                    String id = ((PartnerAgentConfigManager) AgentConfigManager.INSTANCE).getConfig().getAgentId();
                    Path filePath = Paths.get(STORAGE_DIR, id + ".session");
                    if (Files.exists(filePath)) {
                        sessionManager = deserialize(id);
                        if (sessionManager == null){
                            throw new ServiceLoadFailedException("SessionManager 加载失败");
                        }
                    } else {
                        sessionManager = new SessionManager();
                        sessionManager.setSingleMetaMessageMap(new HashMap<>());
                        sessionManager.id = id;
                        sessionManager.setShutdownHook();
                        sessionManager.lastUpdatedTime = 0;
                    }
                }
            }
        }
        return sessionManager;
    }

    private void setShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                sessionManager.serialize();
                log.info("[SessionManager] SessionManager 已保存");
            } catch (IOException e) {
                log.error("[SessionManager] 保存 SessionManager 失败: ", e);
            }
        }));
    }

    public void addMetaMessage(String userId, MetaMessage metaMessage) {
        log.debug("[SessionManager] 当前会话历史: {}", JSONObject.toJSONString(singleMetaMessageMap));
        if (singleMetaMessageMap.containsKey(userId)) {
            singleMetaMessageMap.get(userId).add(metaMessage);
        } else {
            singleMetaMessageMap.put(userId, new java.util.ArrayList<>());
            singleMetaMessageMap.get(userId).add(metaMessage);
        }
        log.debug("[SessionManager] 会话历史更新: {}", JSONObject.toJSONString(singleMetaMessageMap));
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
        //先写入到临时文件，如果正常写入，则覆盖正式文件；否则删除临时文件
        Path filePath = getFilePath(this.id + "-temp");
        Files.createDirectories(Path.of(STORAGE_DIR));
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath.toFile()));
            oos.writeObject(this);
            oos.close();
            Path path = getFilePath(this.id);
            Files.move(filePath, path, StandardCopyOption.REPLACE_EXISTING);
            log.info("[SessionManager] SessionManager 已保存到: {}", path);
        } catch (IOException e) {
            Files.delete(filePath);
            log.error("[SessionManager] 序列化保存失败: {}", e.getMessage());
        }
    }

    private static SessionManager deserialize(String id) {
        Path filePath = getFilePath(id);
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath.toFile()))) {
            SessionManager sessionManager = (SessionManager) ois.readObject();
            log.info("[SessionManager] SessionManager 已从文件加载: {}", filePath);
            return sessionManager;
        }catch (IOException | ClassNotFoundException e) {
            log.error("[SessionManager] 读取异常, 读取失败: ", e);
            return null;
        }
    }

    public void resetLastUpdatedTime() {
        lastUpdatedTime = System.currentTimeMillis();
    }

    private static Path getFilePath(String id) {
        return Paths.get(STORAGE_DIR, id + ".session");
    }
}


