package work.slhaf.agent.core.memory;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.constant.ChatConstant;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.exception_handler.GlobalExceptionHandler;
import work.slhaf.agent.common.exception_handler.pojo.GlobalException;
import work.slhaf.agent.common.serialize.PersistableObject;
import work.slhaf.agent.core.memory.pojo.MemoryResult;
import work.slhaf.agent.core.memory.pojo.MemorySliceResult;
import work.slhaf.agent.core.memory.submodule.cache.CacheCore;
import work.slhaf.agent.core.memory.submodule.graph.GraphCore;
import work.slhaf.agent.core.memory.submodule.graph.pojo.MemorySlice;
import work.slhaf.agent.core.memory.submodule.perceive.PerceiveCore;
import work.slhaf.agent.core.memory.submodule.perceive.pojo.User;
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


    private MemoryCore memoryCore;
    private CacheCore cacheCore;
    private GraphCore graphCore;
    private PerceiveCore perceiveCore;

    private HashMap<String, List<EvaluatedSlice>> activatedSlices;

    private MemoryManager() {
    }


    public static MemoryManager getInstance() throws IOException, ClassNotFoundException {
        if (memoryManager == null) {
            synchronized (MemoryManager.class) {
                if (memoryManager == null) {
                    Config config = Config.getConfig();
                    memoryManager = new MemoryManager();
                    memoryManager.setMemoryCore(MemoryCore.getInstance(config.getAgentId()));
                    memoryManager.setCores();
                    memoryManager.setActivatedSlices(new HashMap<>());
                    memoryManager.setShutdownHook();
                    log.info("[MemoryManager] MemoryManager注册完毕...");
                }
            }
        }
        return memoryManager;
    }

    private void setCores() {
        this.setCacheCore(this.getMemoryCore().getCacheCore());
        this.setGraphCore(this.getMemoryCore().getGraphCore());
        this.setPerceiveCore(this.getMemoryCore().getPerceiveCore());
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

    public MemoryResult selectMemory(String topicPathStr) {
        MemoryResult memoryResult;
        List<String> topicPath = List.of(topicPathStr.split("->"));
        try {
            List<String> path = new ArrayList<>(topicPath);
            //每日刷新缓存
            cacheCore.checkCacheDate();
            //检测缓存并更新计数, 查看是否需要放入缓存
            cacheCore.updateCacheCounter(path);
            //查看是否存在缓存，如果存在，则直接返回
            if ((memoryResult = cacheCore.selectCache(path)) != null) {
                return memoryResult;
            }
            memoryResult = graphCore.selectMemory(path);
            //尝试更新缓存
            cacheCore.updateCache(topicPath, memoryResult);
        } catch (Exception e) {
            log.error("[MemoryManager] selectMemory error: ", e);
            log.error("[MemoryManager] 路径: {}", topicPathStr);
            log.error("[MemoryManager] 主题树: {}", getTopicTree());
            memoryResult = new MemoryResult();
            memoryResult.setRelatedMemorySliceResult(new ArrayList<>());
            memoryResult.setMemorySliceResult(new CopyOnWriteArrayList<>());
            GlobalExceptionHandler.writeExceptionState(new GlobalException(e.getLocalizedMessage()));
        }
        return cacheFilter(memoryResult);
    }

    public MemoryResult selectMemory(LocalDate date) throws IOException, ClassNotFoundException {
        return cacheFilter(graphCore.selectMemory(date));
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
        graphCore.getSelectedSlices().clear();
    }

    public String getUserId(String userInfo, String nickName) {
        String userId = null;
        for (User user : perceiveCore.getUsers()) {
            if (user.getInfo().contains(userInfo)) {
                userId = user.getUuid();
            }
        }
        if (userId == null) {
            User newUser = setNewUser(userInfo, nickName);
            perceiveCore.getUsers().add(newUser);
            userId = newUser.getUuid();
        }
        return userId;
    }

    public List<Message> getChatMessages() {
        return memoryCore.getChatMessages();
    }

    public void setChatMessages(List<Message> chatMessages) {
        memoryCore.setChatMessages(chatMessages);
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
        return graphCore.getTopicTree();
    }

    public HashMap<LocalDateTime, String> getDialogMap() {
        return cacheCore.getDialogMap();
    }

    public ConcurrentHashMap<LocalDateTime, String> getUserDialogMap(String userId) {
        return cacheCore.getUserDialogMap().get(userId);
    }

    public void insertSlice(MemorySlice memorySlice, String topicPath) {
        sliceInsertLock.lock();
        List<String> topicPathList = Arrays.stream(topicPath.split("->")).toList();
        try {
            //检查是否存在当天对应的memorySlice并确定是否插入
            //每日刷新缓存
            cacheCore.checkCacheDate();
            //如果topicPath在memorySliceCache中存在对应缓存，由于进行的插入操作，则需要移除该缓存，但不清除相关计数
            cacheCore.clearCacheByTopicPath(topicPathList);
            graphCore.insertMemory(topicPathList, memorySlice);
            if (!memorySlice.isPrivate()) {
                cacheCore.updateUserDialogMap(memorySlice);
            }
        } catch (Exception e) {
            log.error("[MemoryManager] 插入记忆时出错: ", e);
            GlobalExceptionHandler.writeExceptionState(new GlobalException("插入记忆时出错: " + e.getLocalizedMessage()));
        }
        log.debug("[MemoryManager] 插入切片: {}, 路径: {}", memorySlice, topicPath);
        sliceInsertLock.unlock();
    }

    public void cleanMessage(List<Message> messages) {
        messageLock.lock();
        memoryCore.getChatMessages().removeAll(messages);
        messageLock.unlock();
    }

    public void updateDialogMap(LocalDateTime dateTime, String newDialogCache) {
        cacheCore.updateDialogMap(dateTime, newDialogCache);
    }

    private void save() throws IOException {
        memoryCore.serialize();
    }

    public void updateActivatedSlices(String userId, List<EvaluatedSlice> memorySlices) {
        memoryManager.getActivatedSlices().put(userId, memorySlices);
        log.debug("[MemoryManager] 已更新激活切片, userId: {}", userId);
    }

    public User getUser(String id) {
        for (User user : perceiveCore.getUsers()) {
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
        cacheCore.getDialogMap().forEach((dateTime, dialog) -> str.append("\n\n").append("[").append(dateTime).append("]\n")
                .append(dialog));
        return str.toString();
    }

    public String getUserDialogMapStr(String userId) {
        if (cacheCore.getUserDialogMap().containsKey(userId)) {
            StringBuilder str = new StringBuilder();
            Collection<String> dialogMapValues = cacheCore.getDialogMap().values();
            cacheCore.getUserDialogMap().get(userId).forEach((dateTime, dialog) -> {
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
        return cacheCore.getUserDialogMap().size() <= 1;
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
