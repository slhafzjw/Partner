package work.slhaf.partner.core.cognation;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.common.chat.constant.ChatConstant;
import work.slhaf.partner.api.factory.capability.annotation.CoordinateManager;
import work.slhaf.partner.api.factory.capability.annotation.Coordinated;
import work.slhaf.partner.common.exception_handler.GlobalExceptionHandler;
import work.slhaf.partner.common.exception_handler.pojo.GlobalException;
import work.slhaf.partner.core.cognation.cognation.CognationCore;
import work.slhaf.partner.core.cognation.common.pojo.MemoryResult;
import work.slhaf.partner.core.cognation.common.pojo.MemorySliceResult;
import work.slhaf.partner.core.cognation.submodule.cache.CacheCore;
import work.slhaf.partner.core.cognation.submodule.dispatch.DispatchCore;
import work.slhaf.partner.core.cognation.submodule.memory.MemoryCore;
import work.slhaf.partner.core.cognation.submodule.memory.pojo.MemorySlice;
import work.slhaf.partner.core.cognation.submodule.perceive.PerceiveCore;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static work.slhaf.partner.common.util.ExtractUtil.extractUserId;

@Data
@Slf4j
@CoordinateManager
public class CoordinatedManager implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static volatile CoordinatedManager coordinatedManager;
    private final Lock sliceInsertLock = new ReentrantLock();

    private CognationCore cognationCore;
    private CacheCore cacheCore;
    private MemoryCore memoryCore;
    private PerceiveCore perceiveCore;
    private DispatchCore dispatchCore;

    private CoordinatedManager() {
    }


    public static CoordinatedManager getInstance() throws IOException, ClassNotFoundException {
        if (coordinatedManager == null) {
            synchronized (CoordinatedManager.class) {
                if (coordinatedManager == null) {
                    coordinatedManager = new CoordinatedManager();
                    coordinatedManager.setCognationCore(CognationCore.getInstance());
                    coordinatedManager.setCores();
                    log.info("[CoordinatedManager] MemoryManager注册完毕...");
                }
            }
        }
        return coordinatedManager;
    }

    private void setCores() {
        this.setCacheCore(this.getCognationCore().getCacheCore());
        this.setMemoryCore(this.getCognationCore().getMemoryCore());
        this.setPerceiveCore(this.getCognationCore().getPerceiveCore());
    }


    @Coordinated(capability = "memory")
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
            log.error("[CoordinatedManager] selectMemory error: ", e);
            log.error("[CoordinatedManager] 路径: {}", topicPathStr);
            log.error("[CoordinatedManager] 主题树: {}", memoryCore.getTopicTree());
            memoryResult = new MemoryResult();
            memoryResult.setRelatedMemorySliceResult(new ArrayList<>());
            memoryResult.setMemorySliceResult(new CopyOnWriteArrayList<>());
            GlobalExceptionHandler.writeExceptionState(new GlobalException(e.getLocalizedMessage()));
        }
        return cacheFilter(memoryResult);
    }

    @Coordinated(capability = "memory")
    public MemoryResult selectMemory(LocalDate date) throws IOException, ClassNotFoundException {
        return cacheFilter(memoryCore.selectMemory(date));
    }

    private MemoryResult cacheFilter(MemoryResult memoryResult) {
        //过滤掉与缓存重复的切片
        CopyOnWriteArrayList<MemorySliceResult> memorySliceResult = memoryResult.getMemorySliceResult();
        List<MemorySlice> relatedMemorySliceResult = memoryResult.getRelatedMemorySliceResult();
        cacheCore.getDialogMap().forEach((k, v) -> {
            memorySliceResult.removeIf(m -> m.getMemorySlice().getSummary().equals(v));
            relatedMemorySliceResult.removeIf(m -> m.getSummary().equals(v));
        });
        return memoryResult;
    }


    @Coordinated(capability = "memory")
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
            log.error("[CoordinatedManager] 插入记忆时出错: ", e);
            GlobalExceptionHandler.writeExceptionState(new GlobalException("插入记忆时出错: " + e.getLocalizedMessage()));
        }
        log.debug("[CoordinatedManager] 插入切片: {}, 路径: {}", memorySlice, topicPath);
        sliceInsertLock.unlock();
    }


    private boolean isCacheSingleUser() {
        return cacheCore.getUserDialogMap().size() <= 1;
    }

    @Coordinated(capability = "cognation")
    public boolean isSingleUser() {
        return isCacheSingleUser() && isChatMessagesSingleUser();
    }

    private boolean isChatMessagesSingleUser() {
        Set<String> userIdSet = new HashSet<>();
        cognationCore.getChatMessages().forEach(m -> {
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
