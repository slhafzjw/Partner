package work.slhaf.agent.core.cognation;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.constant.ChatConstant;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.exception_handler.GlobalExceptionHandler;
import work.slhaf.agent.common.exception_handler.pojo.GlobalException;
import work.slhaf.agent.common.serialize.PersistableObject;
import work.slhaf.agent.core.cognation.common.exception.UserNotExistsException;
import work.slhaf.agent.core.cognation.common.pojo.ActiveData;
import work.slhaf.agent.core.cognation.common.pojo.MemoryResult;
import work.slhaf.agent.core.cognation.common.pojo.MemorySliceResult;
import work.slhaf.agent.core.cognation.submodule.cache.CacheCapability;
import work.slhaf.agent.core.cognation.submodule.cache.CacheCore;
import work.slhaf.agent.core.cognation.submodule.memory.MemoryCapability;
import work.slhaf.agent.core.cognation.submodule.memory.MemoryCore;
import work.slhaf.agent.core.cognation.submodule.memory.pojo.MemorySlice;
import work.slhaf.agent.core.cognation.submodule.perceive.PerceiveCapability;
import work.slhaf.agent.core.cognation.submodule.perceive.PerceiveCore;
import work.slhaf.agent.core.cognation.submodule.perceive.pojo.User;
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
public class CognationManager extends PersistableObject implements CacheCapability, MemoryCapability, PerceiveCapability, CognationCapability {

    @Serial
    private static final long serialVersionUID = 1L;

    private static volatile CognationManager cognationManager;
    private final Lock sliceInsertLock = new ReentrantLock();
    public final Lock messageLock = new ReentrantLock();


    private CognationCore cognationCore;
    private CacheCore cacheCore;
    private MemoryCore memoryCore;
    private PerceiveCore perceiveCore;

    private ActiveData activeData;

    private CognationManager() {
    }


    public static CognationManager getInstance() throws IOException, ClassNotFoundException {
        if (cognationManager == null) {
            synchronized (CognationManager.class) {
                if (cognationManager == null) {
                    Config config = Config.getConfig();
                    cognationManager = new CognationManager();
                    cognationManager.setCognationCore(CognationCore.getInstance(config.getAgentId()));
                    cognationManager.setCores();
                    cognationManager.setActiveData(new ActiveData());
                    cognationManager.setShutdownHook();
                    log.info("[CognationManager] MemoryManager注册完毕...");
                }
            }
        }
        return cognationManager;
    }

    private void setCores() {
        this.setCacheCore(this.getCognationCore().getCacheCore());
        this.setMemoryCore(this.getCognationCore().getMemoryCore());
        this.setPerceiveCore(this.getCognationCore().getPerceiveCore());
    }

    private void setShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                cognationManager.save();
                log.info("[CognationManager] MemoryGraph已保存");
            } catch (IOException e) {
                log.error("[CognationManager] 保存MemoryGraph失败: ", e);
            }
        }));
    }

    @Override
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
            memoryResult = memoryCore.selectMemory(path);
            //尝试更新缓存
            cacheCore.updateCache(topicPath, memoryResult);
        } catch (Exception e) {
            log.error("[CognationManager] selectMemory error: ", e);
            log.error("[CognationManager] 路径: {}", topicPathStr);
            log.error("[CognationManager] 主题树: {}", getTopicTree());
            memoryResult = new MemoryResult();
            memoryResult.setRelatedMemorySliceResult(new ArrayList<>());
            memoryResult.setMemorySliceResult(new CopyOnWriteArrayList<>());
            GlobalExceptionHandler.writeExceptionState(new GlobalException(e.getLocalizedMessage()));
        }
        return cacheFilter(memoryResult);
    }

    @Override
    public MemoryResult selectMemory(LocalDate date) throws IOException, ClassNotFoundException {
        return cacheFilter(memoryCore.selectMemory(date));
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

    @Override
    public void cleanSelectedSliceFilter() {
        memoryCore.getSelectedSlices().clear();
    }

    @Override
    public User getUser(String userInfo, String client) {
        return perceiveCore.selectUser(userInfo, client);
    }

    @Override
    public List<Message> getChatMessages() {
        return cognationCore.getChatMessages();
    }

    @Override
    public void setChatMessages(List<Message> chatMessages) {
        cognationCore.setChatMessages(chatMessages);
    }

    @Override
    public String getTopicTree() {
        return memoryCore.getTopicTree();
    }

    @Override
    public HashMap<LocalDateTime, String> getDialogMap() {
        return cacheCore.getDialogMap();
    }

    @Override
    public ConcurrentHashMap<LocalDateTime, String> getUserDialogMap(String userId) {
        return cacheCore.getUserDialogMap().get(userId);
    }

    @Override
    public void insertSlice(MemorySlice memorySlice, String topicPath) {
        sliceInsertLock.lock();
        List<String> topicPathList = Arrays.stream(topicPath.split("->")).toList();
        try {
            //检查是否存在当天对应的memorySlice并确定是否插入
            //每日刷新缓存
            cacheCore.checkCacheDate();
            //如果topicPath在memorySliceCache中存在对应缓存，由于进行的插入操作，则需要移除该缓存，但不清除相关计数
            cacheCore.clearCacheByTopicPath(topicPathList);
            memoryCore.insertMemory(topicPathList, memorySlice);
            if (!memorySlice.isPrivate()) {
                cacheCore.updateUserDialogMap(memorySlice);
            }
        } catch (Exception e) {
            log.error("[CognationManager] 插入记忆时出错: ", e);
            GlobalExceptionHandler.writeExceptionState(new GlobalException("插入记忆时出错: " + e.getLocalizedMessage()));
        }
        log.debug("[CognationManager] 插入切片: {}, 路径: {}", memorySlice, topicPath);
        sliceInsertLock.unlock();
    }

    @Override
    public void cleanMessage(List<Message> messages) {
        messageLock.lock();
        cognationCore.getChatMessages().removeAll(messages);
        messageLock.unlock();
    }

    @Override
    public void updateDialogMap(LocalDateTime dateTime, String newDialogCache) {
        cacheCore.updateDialogMap(dateTime, newDialogCache);
    }

    private void save() throws IOException {
        cognationCore.serialize();
    }

    @Override
    public void updateActivatedSlices(String userId, List<EvaluatedSlice> memorySlices) {
        activeData.updateActivatedSlices(userId, memorySlices);
        log.debug("[CognationManager] 已更新激活切片, userId: {}", userId);
    }

    @Override
    public User getUser(String id) {
        User user = perceiveCore.selectUser(id);
        if (user == null) {
            throw new UserNotExistsException("[CognationManager] 用户不存在: " + id);
        }
        return user;
    }

    @Override
    public String getActivatedSlicesStr(String userId) {
        return activeData.getActivatedSlicesStr(userId);
    }

    @Override
    public String getDialogMapStr() {
        StringBuilder str = new StringBuilder();
        cacheCore.getDialogMap().forEach((dateTime, dialog) -> str.append("\n\n").append("[").append(dateTime).append("]\n")
                .append(dialog));
        return str.toString();
    }

    @Override
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

    @Override
    public boolean isSingleUser() {
        return isCacheSingleUser() && isChatMessagesSingleUser();
    }

    private boolean isChatMessagesSingleUser() {
        Set<String> userIdSet = new HashSet<>();
        cognationManager.getChatMessages().forEach(m -> {
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

    @Override
    public User addUser(String userInfo, String platform, String userNickName) {
        return perceiveCore.addUser(userInfo, platform, userNickName);
    }

    @Override
    public void updateUser(User tempUser) {
        perceiveCore.updateUser(tempUser);
    }

    @Override
    public HashMap<String, List<EvaluatedSlice>> getActivatedSlices() {
        return activeData.getActivatedSlices();
    }

    @Override
    public void clearActivatedSlices(String userId) {
        activeData.clearActivatedSlices(userId);
    }

    @Override
    public boolean hasActivatedSlices(String userId) {
        return activeData.hasActivatedSlices(userId);
    }

    @Override
    public int getActivatedSlicesSize(String userId) {
        return activeData.getActivatedSlices().get(userId).size();
    }

    @Override
    public List<EvaluatedSlice> getActivatedSlices(String userId) {
        return activeData.getActivatedSlices().get(userId);
    }
}
