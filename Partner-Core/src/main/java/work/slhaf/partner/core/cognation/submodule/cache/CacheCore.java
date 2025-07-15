package work.slhaf.partner.core.cognation.submodule.cache;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.common.serialize.PersistableObject;
import work.slhaf.partner.core.cognation.common.pojo.MemoryResult;
import work.slhaf.partner.core.cognation.submodule.memory.pojo.MemorySlice;

import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class CacheCore extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 近两日的对话总结缓存, 用于为大模型提供必要的记忆补充, hashmap以切片的存储时间为键，总结为值
     * 该部分作为'主LLM'system prompt常驻
     * 该部分作为近两日的整体对话缓存, 不区分用户
     */
    private HashMap<LocalDateTime, String> dialogMap = new HashMap<>();

    /**
     * 近两日的区分用户的对话总结缓存，在prompt结构上比dialogMap层级深一层, dialogMap更具近两日整体对话的摘要性质
     */
    private ConcurrentHashMap<String/*userId*/, ConcurrentHashMap<LocalDateTime, String>> userDialogMap = new ConcurrentHashMap<>();

    /**
     * memorySliceCache计数器，每日清空
     */
    private ConcurrentHashMap<List<String> /*触发查询的主题列表*/, Integer> memoryNodeCacheCounter = new ConcurrentHashMap<>();

    /**
     * 记忆切片缓存，每日清空
     * 用于记录作为终点节点调用次数最多的记忆节点的切片数据
     */
    private ConcurrentHashMap<List<String> /*主题路径*/, MemoryResult /*切片列表*/> memorySliceCache = new ConcurrentHashMap<>();

    /**
     * 缓存日期
     */
    private LocalDate cacheDate;

    /**
     * 已被选中的切片时间戳集合，需要及时清理
     */
    private Set<Long> selectedSlices = new HashSet<>();

    public void updateCacheCounter(List<String> topicPath) {
        if (memoryNodeCacheCounter.containsKey(topicPath)) {
            Integer tempCount = memoryNodeCacheCounter.get(topicPath);
            memoryNodeCacheCounter.put(topicPath, ++tempCount);
        } else {
            memoryNodeCacheCounter.put(topicPath, 1);
        }
    }

    public void checkCacheDate() {
        if (cacheDate == null || cacheDate.isBefore(LocalDate.now())) {
            memorySliceCache.clear();
            memoryNodeCacheCounter.clear();
            cacheDate = LocalDate.now();
        }
    }

    public void updateDialogMap(LocalDateTime dateTime, String newDialogCache) {
        List<LocalDateTime> keysToRemove = new ArrayList<>();
        dialogMap.forEach((k, v) -> {
            if (dateTime.minusDays(2).isAfter(k)) {
                keysToRemove.add(k);
            }
        });
        for (LocalDateTime temp : keysToRemove) {
            dialogMap.remove(temp);
        }
        keysToRemove.clear();
        //放入新缓存
        dialogMap.put(dateTime, newDialogCache);
    }

    public void updateCache(List<String> topicPath, MemoryResult memoryResult) {
        Integer tempCount = memoryNodeCacheCounter.get(topicPath);
        if (tempCount == null) {
            log.warn("[CacheCore] tempCount为null? memoryNodeCacheCounter: {}; topicPath: {}", memoryNodeCacheCounter, topicPath);
            return;
        }
        if (tempCount >= 5) {
            memorySliceCache.put(topicPath, memoryResult);
        }
    }

    public void updateUserDialogMap(MemorySlice slice) {
        String summary = slice.getSummary();
        LocalDateTime now = LocalDateTime.now();

        //更新userDialogMap
        //移除两天前上下文缓存(切片总结)
        List<LocalDateTime> keysToRemove = new ArrayList<>();
        userDialogMap.forEach((k, v) -> v.forEach((i, j) -> {
            if (now.minusDays(2).isAfter(i)) {
                keysToRemove.add(i);
            }
        }));
        for (LocalDateTime dateTime : keysToRemove) {
            userDialogMap.forEach((k, v) -> v.remove(dateTime));
        }
        //放入新缓存
        userDialogMap
                .computeIfAbsent(slice.getStartUserId(), k -> new ConcurrentHashMap<>())
                .merge(now, summary, (oldVal, newVal) -> oldVal + " " + newVal);

    }

    public void clearCacheByTopicPath(List<String> topicPath) {
        memorySliceCache.remove(topicPath);
    }

    public MemoryResult selectCache(List<String> path) {
        if (memorySliceCache.containsKey(path)) {
            return memorySliceCache.get(path);
        }
        return null;
    }
}
