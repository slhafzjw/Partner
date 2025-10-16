package work.slhaf.partner.core.memory;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.core.PartnerCore;
import work.slhaf.partner.core.memory.exception.UnExistedDateIndexException;
import work.slhaf.partner.core.memory.exception.UnExistedTopicException;
import work.slhaf.partner.core.memory.pojo.EvaluatedSlice;
import work.slhaf.partner.core.memory.pojo.MemoryResult;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemorySliceResult;
import work.slhaf.partner.core.memory.pojo.node.MemoryNode;
import work.slhaf.partner.core.memory.pojo.node.TopicNode;

import java.io.IOException;
import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@CapabilityCore(value = "memory")
@Getter
@Setter
@Slf4j
public class MemoryCore extends PartnerCore<MemoryCore> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * key: 根主题名称  value: 根主题节点
     */
    private HashMap<String, TopicNode> topicNodes = new HashMap<>();

    /**
     * 用于存储已存在的主题列表，便于记忆查找, 使用根主题名称作为键, 子主题名称集合为值
     * 该部分在'主题提取LLM'的system prompt中常驻
     */
    private HashMap<String /*根主题名*/, LinkedHashSet<String> /*子主题列表*/> existedTopics = new HashMap<>();

    /**
     * 临时的同一对话切片容器, 用于为同一对话内的不同切片提供更新上下文的场所
     */
    private HashMap<String /*对话id, 即slice中的字段'memoryId'*/, List<MemorySlice>> currentDateDialogSlices = new HashMap<>();

    /**
     * 记忆节点的日期索引, 同一日期内按照对话id区分
     */
    private HashMap<LocalDate, Set<String>> dateIndex = new HashMap<>();

    /**
     * 已被选中的切片时间戳集合，需要及时清理
     */
    private Set<Long> selectedSlices = new HashSet<>();

    private HashMap<String, List<String>> userIndex = new HashMap<>();

    private MemoryCache cache = new MemoryCache();

    private final Lock sliceInsertLock = new ReentrantLock();

    public MemoryCore() throws IOException, ClassNotFoundException {
    }


    @CapabilityMethod
    public MemoryResult selectMemory(LocalDate date) throws IOException, ClassNotFoundException {
        MemoryResult memoryResult = new MemoryResult();
        CopyOnWriteArrayList<MemorySliceResult> targetSliceList = new CopyOnWriteArrayList<>();
        //加载节点并获取记忆切片列表
        List<List<MemorySlice>> currentDateDialogSlices = loadSlicesByDate(date);
        for (List<MemorySlice> value : currentDateDialogSlices) {
            for (MemorySlice memorySlice : value) {
                if (selectedSlices.contains(memorySlice.getTimestamp())) {
                    continue;
                }
                MemorySliceResult memorySliceResult = new MemorySliceResult();
                memorySliceResult.setMemorySlice(memorySlice);
                targetSliceList.add(memorySliceResult);
                selectedSlices.add(memorySlice.getTimestamp());
            }
        }
        memoryResult.setMemorySliceResult(targetSliceList);
        return cacheFilter(memoryResult);
    }

    @CapabilityMethod
    public void insertSlice(MemorySlice memorySlice, String topicPath) {
        sliceInsertLock.lock();
        List<String> topicPathList = Arrays.stream(topicPath.split("->")).toList();
        try {
            //检查是否存在当天对应的memorySlice并确定是否插入
            //每日刷新缓存
            checkCacheDate();
            //如果topicPath在memorySliceCache中存在对应缓存，由于进行的插入操作，则需要移除该缓存，但不清除相关计数
            clearCacheByTopicPath(topicPathList);
            insertMemory(topicPathList, memorySlice);
            if (!memorySlice.isPrivate()) {
                updateUserDialogMap(memorySlice);
            }
        } catch (Exception e) {
            log.error("[CoordinatedManager] 插入记忆时出错: ", e);
        }
        log.debug("[CoordinatedManager] 插入切片: {}, 路径: {}", memorySlice, topicPath);
        sliceInsertLock.unlock();
    }

    @CapabilityMethod
    public String getTopicTree() {
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, TopicNode> entry : topicNodes.entrySet()) {
            String rootName = entry.getKey();
            TopicNode rootNode = entry.getValue();
            stringBuilder.append(rootName).append("[root]").append("\r\n");
            printSubTopicsTreeFormat(rootNode, "", stringBuilder);
        }
        return stringBuilder.toString();
    }

    @CapabilityMethod
    public void updateDialogMap(LocalDateTime dateTime, String newDialogCache) {
        List<LocalDateTime> keysToRemove = new ArrayList<>();
        HashMap<LocalDateTime, String> dialogMap = cache.dialogMap;
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

    @CapabilityMethod
    public HashMap<LocalDateTime, String> getDialogMap() {
        return cache.dialogMap;
    }

    @CapabilityMethod
    public ConcurrentHashMap<LocalDateTime, String> getUserDialogMap(String userId) {
        return cache.userDialogMap.get(userId);
    }

    @CapabilityMethod
    public String getDialogMapStr() {
        StringBuilder str = new StringBuilder();
        this.getDialogMap().forEach((dateTime, dialog) -> str.append("\n\n").append("[").append(dateTime).append("]\n")
                .append(dialog));
        return str.toString();
    }

    @CapabilityMethod
    public String getUserDialogMapStr(String userId) {
        ConcurrentHashMap<String, ConcurrentHashMap<LocalDateTime, String>> userDialogMap = cache.userDialogMap;
        if (userDialogMap.containsKey(userId)) {
            StringBuilder str = new StringBuilder();
            Collection<String> dialogMapValues = this.getDialogMap().values();
            userDialogMap.get(userId).forEach((dateTime, dialog) -> {
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

    @CapabilityMethod
    public MemoryResult selectMemory(String topicPathStr) {
        MemoryResult memoryResult;
        List<String> topicPath = List.of(topicPathStr.split("->"));
        try {
            List<String> path = new ArrayList<>(topicPath);
            //每日刷新缓存
            checkCacheDate();
            //检测缓存并更新计数, 查看是否需要放入缓存
            updateCacheCounter(path);
            //查看是否存在缓存，如果存在，则直接返回
            if ((memoryResult = selectCache(path)) != null) {
                return memoryResult;
            }
            memoryResult = selectMemory(path);
            //尝试更新缓存
            updateCache(topicPath, memoryResult);
        } catch (Exception e) {
            log.error("[CoordinatedManager] selectMemory error: ", e);
            log.error("[CoordinatedManager] 路径: {}", topicPathStr);
            log.error("[CoordinatedManager] 主题树: {}", getTopicTree());
            memoryResult = new MemoryResult();
            memoryResult.setRelatedMemorySliceResult(new ArrayList<>());
            memoryResult.setMemorySliceResult(new CopyOnWriteArrayList<>());
        }
        return cacheFilter(memoryResult);
    }

    @CapabilityMethod
    public void updateActivatedSlices(String userId, List<EvaluatedSlice> memorySlices) {
        cache.activatedSlices.put(userId, memorySlices);
        log.debug("[CoordinatedManager] 已更新激活切片, userId: {}", userId);
    }

    @CapabilityMethod
    public String getActivatedSlicesStr(String userId) {
        HashMap<String, List<EvaluatedSlice>> activatedSlices = cache.activatedSlices;
        if (activatedSlices.containsKey(userId)) {
            StringBuilder str = new StringBuilder();
            activatedSlices.get(userId).forEach(slice -> str.append("\n\n").append("[").append(slice.getDate()).append("]\n")
                    .append(slice.getSummary()));
            return str.toString();
        } else {
            return null;
        }
    }

    @CapabilityMethod
    public HashMap<String, List<EvaluatedSlice>> getActivatedSlices() {
        return cache.activatedSlices;
    }

    @CapabilityMethod
    public void clearActivatedSlices(String userId) {
        cache.activatedSlices.remove(userId);
    }

    @CapabilityMethod
    public boolean hasActivatedSlices(String userId) {
        HashMap<String, List<EvaluatedSlice>> activatedSlices = cache.activatedSlices;
        if (!activatedSlices.containsKey(userId)) {
            return false;
        }
        return !activatedSlices.get(userId).isEmpty();
    }

    @CapabilityMethod
    public int getActivatedSlicesSize(String userId) {
        return cache.activatedSlices.get(userId).size();
    }

    @CapabilityMethod
    public List<EvaluatedSlice> getActivatedSlices(String userId) {
        return cache.activatedSlices.get(userId);
    }

    @CapabilityMethod
    public void cleanSelectedSliceFilter() {
        this.selectedSlices.clear();
    }

    private List<List<MemorySlice>> loadSlicesByDate(LocalDate date) throws IOException, ClassNotFoundException {
        if (!dateIndex.containsKey(date)) {
            throw new UnExistedDateIndexException("不存在的日期索引: " + date);
        }
        List<List<MemorySlice>> list = new ArrayList<>();
        for (String memoryNodeId : dateIndex.get(date)) {
            MemoryNode memoryNode = new MemoryNode();
            memoryNode.setMemoryNodeId(memoryNodeId);
            list.add(memoryNode.loadMemorySliceList());
        }
        return list;
    }

    private void printSubTopicsTreeFormat(TopicNode node, String prefix, StringBuilder stringBuilder) {
        if (node.getTopicNodes() == null || node.getTopicNodes().isEmpty()) return;

        List<Map.Entry<String, TopicNode>> entries = new ArrayList<>(node.getTopicNodes().entrySet());
        for (int i = 0; i < entries.size(); i++) {
            boolean last = (i == entries.size() - 1);
            Map.Entry<String, TopicNode> entry = entries.get(i);
            stringBuilder.append(prefix).append(last ? "└── " : "├── ").append(entry.getKey()).append("[").append(entry.getValue().getMemoryNodes().size()).append("]").append("\r\n");
            printSubTopicsTreeFormat(entry.getValue(), prefix + (last ? "    " : "│   "), stringBuilder);
        }
    }

    private void insertMemory(List<String> topicPath, MemorySlice slice) throws IOException, ClassNotFoundException {
        LocalDate now = LocalDate.now();
        boolean hasSlice = false;
        MemoryNode node = null;
        TopicNode lastTopicNode = generateTopicPath(topicPath);
        for (MemoryNode memoryNode : lastTopicNode.getMemoryNodes()) {
            if (now.equals(memoryNode.getLocalDate())) {
                hasSlice = true;
                node = memoryNode;
                break;
            }
        }
        if (!hasSlice) {
            node = new MemoryNode();
            node.setLocalDate(now);
            node.setMemoryNodeId(UUID.randomUUID().toString());
            node.setMemorySliceList(new CopyOnWriteArrayList<>());
            lastTopicNode.getMemoryNodes().add(node);
            lastTopicNode.getMemoryNodes().sort(null);
        }
        node.loadMemorySliceList().add(slice);

        //生成relatedTopicPath
        for (List<String> relatedTopic : slice.getRelatedTopics()) {
            generateTopicPath(relatedTopic);
        }

        updateSlicePrecedent(slice);
        updateDateIndex(slice);
        updateUserIndex(slice);

        node.saveMemorySliceList();

    }

    private void updateUserIndex(MemorySlice slice) {
        String memoryId = slice.getMemoryId();
        String userId = slice.getStartUserId();
        if (!userIndex.containsKey(userId)) {
            List<String> memoryIdSet = new ArrayList<>();
            memoryIdSet.add(memoryId);
            userIndex.put(userId, memoryIdSet);
        } else {
            userIndex.get(userId).add(memoryId);
        }
    }


    private TopicNode generateTopicPath(List<String> topicPath) {
        topicPath = new ArrayList<>(topicPath);
        //查看是否存在根主题节点
        String rootTopic = topicPath.getFirst();
        topicPath.removeFirst();
        if (!topicNodes.containsKey(rootTopic)) {
            synchronized (this) {
                if (!topicNodes.containsKey(rootTopic)) {
                    TopicNode rootNode = new TopicNode();
                    topicNodes.put(rootTopic, rootNode);
                    existedTopics.put(rootTopic, new LinkedHashSet<>());
                }
            }
        }

        TopicNode current = topicNodes.get(rootTopic);
        Set<String> existedTopicNodes = existedTopics.get(rootTopic);
        for (String topic : topicPath) {
            if (existedTopicNodes.contains(topic) && current.getTopicNodes().containsKey(topic)) {
                current = current.getTopicNodes().get(topic);
            } else {
                TopicNode newNode = new TopicNode();
                current.getTopicNodes().put(topic, newNode);
                current = newNode;

                current.setMemoryNodes(new CopyOnWriteArrayList<>());
                current.setTopicNodes(new ConcurrentHashMap<>());
                existedTopicNodes.add(topic);
            }
        }
        return current;
    }

    private void updateSlicePrecedent(MemorySlice slice) {
        String memoryId = slice.getMemoryId();
        //查看是否切换了memoryId
        if (!currentDateDialogSlices.containsKey(memoryId)) {
            List<MemorySlice> memorySliceList = new ArrayList<>();
            currentDateDialogSlices.clear();
            currentDateDialogSlices.put(memoryId, memorySliceList);
        }
        //处理上下文关系
        List<MemorySlice> memorySliceList = currentDateDialogSlices.get(memoryId);
        if (memorySliceList.isEmpty()) {
            memorySliceList.add(slice);
        } else {
            //排序
            memorySliceList.sort(null);
            MemorySlice tempSlice = memorySliceList.getLast();
            //设置私密状态一致
            tempSlice.setPrivate(slice.isPrivate());
            //末尾切片添加当前切片的引用
            tempSlice.setSliceAfter(slice);
            //当前切片添加前序切片的引用
            slice.setSliceBefore(tempSlice);
        }

    }

    private void updateDateIndex(MemorySlice slice) {
        String memoryId = slice.getMemoryId();
        LocalDate date = LocalDate.now();
        if (!dateIndex.containsKey(date)) {
            HashSet<String> memoryIdSet = new HashSet<>();
            memoryIdSet.add(memoryId);
            dateIndex.put(date, memoryIdSet);
        } else {
            dateIndex.get(date).add(memoryId);
        }
    }

    public MemoryResult selectMemory(List<String> path) throws IOException, ClassNotFoundException {
        MemoryResult memoryResult = new MemoryResult();
        CopyOnWriteArrayList<MemorySliceResult> targetSliceList = new CopyOnWriteArrayList<>();
        String targetTopic = path.getLast();
        TopicNode targetParentNode = getTargetParentNode(path, targetTopic);
        List<List<String>> relatedTopics = new ArrayList<>();

        //终点记忆节点
        MemorySliceResult sliceResult = new MemorySliceResult();
        for (MemoryNode memoryNode : targetParentNode.getTopicNodes().get(targetTopic).getMemoryNodes()) {
            List<MemorySlice> endpointMemorySliceList = memoryNode.loadMemorySliceList();
            for (MemorySlice memorySlice : endpointMemorySliceList) {
                if (selectedSlices.contains(memorySlice.getTimestamp())) {
                    continue;
                }
                sliceResult.setSliceBefore(memorySlice.getSliceBefore());
                sliceResult.setMemorySlice(memorySlice);
                sliceResult.setSliceAfter(memorySlice.getSliceAfter());
                targetSliceList.add(sliceResult);
                selectedSlices.add(memorySlice.getTimestamp());
            }
            for (MemorySlice memorySlice : endpointMemorySliceList) {
                if (memorySlice.getRelatedTopics() != null) {
                    relatedTopics.addAll(memorySlice.getRelatedTopics());
                }
            }
        }
        memoryResult.setMemorySliceResult(targetSliceList);

        //邻近节点
        List<MemorySlice> relatedMemorySlice = new ArrayList<>();
        //邻近记忆节点 联系
        for (List<String> relatedTopic : relatedTopics) {
            List<String> tempTopicPath = new ArrayList<>(relatedTopic);
            String tempTargetTopic = tempTopicPath.getLast();
            TopicNode tempTargetParentNode = getTargetParentNode(tempTopicPath, tempTargetTopic);
            //获取终点节点及其最新记忆节点
            TopicNode tempTargetNode = tempTargetParentNode.getTopicNodes().get(tempTopicPath.getLast());
            setRelatedMemorySlices(tempTargetNode, relatedMemorySlice);
        }

        //邻近记忆节点 父级
        setRelatedMemorySlices(targetParentNode, relatedMemorySlice);

        //将上述结果包装为MemoryResult
        memoryResult.setRelatedMemorySliceResult(relatedMemorySlice);
        return memoryResult;
    }

    private void setRelatedMemorySlices(TopicNode targetParentNode, List<MemorySlice> relatedMemorySlice) throws IOException, ClassNotFoundException {
        List<MemoryNode> targetParentMemoryNodes = targetParentNode.getMemoryNodes();
        if (!targetParentMemoryNodes.isEmpty()) {
            for (MemorySlice memorySlice : targetParentMemoryNodes.getFirst().loadMemorySliceList()) {
                if (selectedSlices.contains(memorySlice.getTimestamp())) {
                    continue;
                }
                relatedMemorySlice.add(memorySlice);
                selectedSlices.add(memorySlice.getTimestamp());
            }
        }
    }

    private TopicNode getTargetParentNode(List<String> topicPath, String targetTopic) {
        String topTopic = topicPath.getFirst();
        if (!existedTopics.containsKey(topTopic)) {
            throw new UnExistedTopicException("不存在的主题: " + topTopic);
        }
        TopicNode targetParentNode = topicNodes.get(topTopic);
        topicPath.removeFirst();
        for (String topic : topicPath) {
            if (!existedTopics.get(topTopic).contains(topic)) {
                throw new UnExistedTopicException("不存在的主题: " + topTopic);
            }
        }

        //逐层查找目标主题
        while (!targetParentNode.getTopicNodes().containsKey(targetTopic)) {
            targetParentNode = targetParentNode.getTopicNodes().get(topicPath.getFirst());
            topicPath.removeFirst();
        }
        return targetParentNode;
    }

    public void updateCacheCounter(List<String> topicPath) {
        ConcurrentHashMap<List<String>, Integer> memoryNodeCacheCounter = cache.memoryNodeCacheCounter;
        if (memoryNodeCacheCounter.containsKey(topicPath)) {
            Integer tempCount = memoryNodeCacheCounter.get(topicPath);
            memoryNodeCacheCounter.put(topicPath, ++tempCount);
        } else {
            memoryNodeCacheCounter.put(topicPath, 1);
        }
    }

    private void checkCacheDate() {
        if (cache.cacheDate == null || cache.cacheDate.isBefore(LocalDate.now())) {
            cache.memorySliceCache.clear();
            cache.memoryNodeCacheCounter.clear();
            cache.cacheDate = LocalDate.now();
        }
    }

    private void updateCache(List<String> topicPath, MemoryResult memoryResult) {
        ConcurrentHashMap<List<String>, Integer> memoryNodeCacheCounter = cache.memoryNodeCacheCounter;
        Integer tempCount = memoryNodeCacheCounter.get(topicPath);
        if (tempCount == null) {
            log.warn("[CacheCore] tempCount为null? memoryNodeCacheCounter: {}; topicPath: {}", memoryNodeCacheCounter, topicPath);
            return;
        }
        if (tempCount >= 5) {
            cache.memorySliceCache.put(topicPath, memoryResult);
        }
    }

    private void updateUserDialogMap(MemorySlice slice) {
        String summary = slice.getSummary();
        LocalDateTime now = LocalDateTime.now();
        ConcurrentHashMap<String, ConcurrentHashMap<LocalDateTime, String>> userDialogMap = cache.userDialogMap;

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

    private void clearCacheByTopicPath(List<String> topicPath) {
        cache.memorySliceCache.remove(topicPath);
    }

    private MemoryResult selectCache(List<String> path) {
        ConcurrentHashMap<List<String>, MemoryResult> memorySliceCache = cache.memorySliceCache;
        if (memorySliceCache.containsKey(path)) {
            return memorySliceCache.get(path);
        }
        return null;
    }

    @Override
    protected String getCoreKey() {
        return "memory-core";
    }

    public ConcurrentHashMap<String, ConcurrentHashMap<LocalDateTime, String>> getUserDialogMap() {
        return cache.userDialogMap;
    }


    private MemoryResult cacheFilter(MemoryResult memoryResult) {
        //过滤掉与缓存重复的切片
        CopyOnWriteArrayList<MemorySliceResult> memorySliceResult = memoryResult.getMemorySliceResult();
        List<MemorySlice> relatedMemorySliceResult = memoryResult.getRelatedMemorySliceResult();
        cache.dialogMap.forEach((k, v) -> {
            memorySliceResult.removeIf(m -> m.getMemorySlice().getSummary().equals(v));
            relatedMemorySliceResult.removeIf(m -> m.getSummary().equals(v));
        });
        return memoryResult;
    }

    @SuppressWarnings("FieldMayBeFinal")
    private static class MemoryCache {

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

        private HashMap<String, List<EvaluatedSlice>> activatedSlices = new HashMap<>();

        private MemoryCache() {
        }
    }
}
