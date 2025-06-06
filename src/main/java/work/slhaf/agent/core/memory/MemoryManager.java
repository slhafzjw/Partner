package work.slhaf.agent.core.memory;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.constant.ChatConstant;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.serialize.PersistableObject;
import work.slhaf.agent.core.memory.pojo.MemoryResult;
import work.slhaf.agent.core.memory.pojo.MemorySlice;
import work.slhaf.agent.core.memory.pojo.MemorySliceResult;
import work.slhaf.agent.core.memory.pojo.User;
import work.slhaf.agent.shared.memory.EvaluatedSlice;

import java.io.IOException;
import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static work.slhaf.agent.common.util.ExtractUtil.extractUserId;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class MemoryManager extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private static volatile MemoryManager memoryManager;
    private final Lock sliceInsertLock = new ReentrantLock();
    public final Lock messageLock = new ReentrantLock();


    private MemoryGraph memoryGraph;
    private HashMap<String, List<EvaluatedSlice>> activatedSlices;

    private MemoryManager() {
    }


    public static MemoryManager getInstance() throws IOException, ClassNotFoundException {
        if (memoryManager == null) {
            synchronized (MemoryManager.class) {
                if (memoryManager == null) {
                    Config config = Config.getConfig();
                    memoryManager = new MemoryManager();
                    memoryManager.setMemoryGraph(MemoryGraph.getInstance(config.getAgentId()));
                    memoryManager.setActivatedSlices(new HashMap<>());
                    memoryManager.setShutdownHook();
                    log.info("[MemoryManager] MemoryManager注册完毕...");
                }
            }
        }
        return memoryManager;
    }

    private void setShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                memoryManager.save();
                log.info("[MemoryManager] MemoryGraph已保存");
            } catch (IOException e) {
                log.error("[MemoryManager] 保存MemoryGraph失败: ", e);
            }
        }));
    }

    public MemoryResult selectMemory(String path) {
        return cacheFilter(memoryGraph.selectMemory(path));
    }

    public MemoryResult selectMemory(LocalDate date) throws IOException, ClassNotFoundException {
        return cacheFilter(memoryGraph.selectMemory(date));
    }

    private MemoryResult cacheFilter(MemoryResult memoryResult) {
        //过滤掉与缓存重复的切片
        CopyOnWriteArrayList<MemorySliceResult> memorySliceResult = memoryResult.getMemorySliceResult();
        List<MemorySlice> relatedMemorySliceResult = memoryResult.getRelatedMemorySliceResult();
        getDialogMap().forEach((k, v) -> {
            memorySliceResult.removeIf(m -> m.getMemorySlice().getSummary().equals(v));
            relatedMemorySliceResult.removeIf(m -> m.getSummary().equals(v));
        });
        return memoryResult;
    }

    public void cleanSelectedSliceFilter() {
        memoryGraph.getSelectedSlices().clear();
    }

    public String getUserId(String userInfo, String nickName) {
        String userId = null;
        for (User user : memoryGraph.getUsers()) {
            if (user.getInfo().contains(userInfo)) {
                userId = user.getUuid();
            }
        }
        if (userId == null) {
            User newUser = setNewUser(userInfo, nickName);
            memoryGraph.getUsers().add(newUser);
            userId = newUser.getUuid();
        }
        return userId;
    }

    public List<Message> getChatMessages() {
        return memoryGraph.getChatMessages();
    }

    public void setChatMessages(List<Message> chatMessages) {
        memoryGraph.setChatMessages(chatMessages);
    }

    private static User setNewUser(String userInfo, String nickName) {
        User newUser = new User();
        newUser.setUuid(UUID.randomUUID().toString());
        List<String> infoList = new ArrayList<>();
        infoList.add(userInfo);
        newUser.setInfo(infoList);
        newUser.setNickName(nickName);
        return newUser;
    }

    public String getTopicTree() {
        return memoryGraph.getTopicTree();
    }

    public HashMap<LocalDateTime, String> getDialogMap() {
        return memoryGraph.getDialogMap();
    }

    public ConcurrentHashMap<LocalDateTime, String> getUserDialogMap(String userId) {
        return memoryGraph.getUserDialogMap().get(userId);
    }

    public void insertSlice(MemorySlice memorySlice, String topicPath) {
        sliceInsertLock.lock();
        List<String> topicPathList = Arrays.stream(topicPath.split("->")).toList();
        memoryGraph.insertMemory(topicPathList, memorySlice);
        log.debug("[MemoryManager] 插入切片: {}, 路径: {}", memorySlice, topicPath);
        sliceInsertLock.unlock();
    }

    public void cleanMessage(List<Message> messages) {
        messageLock.lock();
        memoryGraph.getChatMessages().removeAll(messages);
        messageLock.unlock();
    }

    public void updateDialogMap(LocalDateTime dateTime, String newDialogCache) {
        memoryGraph.updateDialogMap(dateTime, newDialogCache);
    }

    public void save() throws IOException {
        memoryGraph.serialize();
    }

    public void updateActivatedSlices(String userId, List<EvaluatedSlice> memorySlices) {
        memoryManager.getActivatedSlices().put(userId, memorySlices);
        log.debug("[MemoryManager] 已更新激活切片, userId: {}", userId);
    }

    public User getUser(String id) {
        for (User user : memoryGraph.getUsers()) {
            if (user.getUuid().equals(id)) {
                return user;
            }
        }
        return null;
    }

    public String getActivatedSlicesStr(String userId) {
        if (memoryManager.getActivatedSlices().containsKey(userId)) {
            StringBuilder str = new StringBuilder();
            memoryManager.getActivatedSlices().get(userId).forEach(slice -> str.append("\n\n").append("[").append(slice.getDate()).append("]\n")
                    .append(slice.getSummary()));
            return str.toString();
        } else {
            return null;
        }
    }

    public String getDialogMapStr() {
        StringBuilder str = new StringBuilder();
        memoryGraph.getDialogMap().forEach((dateTime, dialog) -> str.append("\n\n").append("[").append(dateTime).append("]\n")
                .append(dialog));
        return str.toString();
    }

    public String getUserDialogMapStr(String userId) {
        if (memoryGraph.getUserDialogMap().containsKey(userId)) {
            StringBuilder str = new StringBuilder();
            Collection<String> dialogMapValues = memoryGraph.getDialogMap().values();
            memoryGraph.getUserDialogMap().get(userId).forEach((dateTime, dialog) -> {
                if (dialogMapValues.contains(dialog)) {
                    return;
                }
                str.append("\n\n").append("[").append(dateTime).append("]\n")
                        .append(dialog);
            });
            return str.toString();
        } else {
            return null;
        }
    }

    private boolean isCacheSingleUser() {
        return memoryGraph.getUserDialogMap().size() <= 1;
    }

    public boolean isSingleUser() {
        return isCacheSingleUser() && isChatMessagesSingleUser();
    }

    private boolean isChatMessagesSingleUser() {
        Set<String> userIdSet = new HashSet<>();
        memoryManager.getChatMessages().forEach(m -> {
            if (m.getRole().equals(ChatConstant.Character.ASSISTANT)) {
                return;
            }
            String userId = extractUserId(m.getContent());
            if (userId == null || userId.isEmpty()) {
                return;
            }
            userIdSet.add(userId);
        });
        return userIdSet.size() <= 1;
    }
}
