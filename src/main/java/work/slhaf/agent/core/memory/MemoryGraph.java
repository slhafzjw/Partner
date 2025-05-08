package work.slhaf.agent.core.memory;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.pojo.PersistableObject;
import work.slhaf.agent.core.memory.exception.UnExistedTopicException;
import work.slhaf.agent.core.memory.node.MemoryNode;
import work.slhaf.agent.core.memory.node.TopicNode;
import work.slhaf.agent.core.memory.pojo.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class MemoryGraph extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String STORAGE_DIR = "./data/memory/";
    private String id;
    /**
     * key: 根主题名称  value: 根主题节点
     */
    private HashMap<String, TopicNode> topicNodes;
    private static MemoryGraph memoryGraph;

    /**
     * 用于存储已存在的主题列表，便于记忆查找, 使用根主题名称作为键, 子主题名称集合为值
     * 该部分在'主题提取LLM'的system prompt中常驻
     */
    private HashMap<String /*根主题名*/, LinkedHashSet<String> /*子主题列表*/> existedTopics;

    /**
     * 记忆节点的日期索引, 同一日期内按照对话id区分
     * 同时作为临时的同一对话切片容器, 用于为同一对话内的不同切片提供更新上下文的场所
     */
    private HashMap<LocalDate, HashMap<String /*对话id, 即slice中的字段'memoryId'*/, List<MemorySlice>>> dateIndex;

    /**
     * 近两日的对话总结缓存, 用于为大模型提供必要的记忆补充, hashmap以切片的存储时间为键，总结为值
     * 该部分作为'主LLM'system prompt常驻
     * 该部分作为近两日的整体对话缓存, 不区分用户
     */
    private HashMap<LocalDateTime, String> dialogMap;

    /**
     * 近两日的区分用户的对话总结缓存，在prompt结构上比dialogMap层级深一层, dialogMap更具近两日整体对话的摘要性质
     */
    private ConcurrentHashMap<String/*userId*/, ConcurrentHashMap<LocalDateTime, String>> userDialogMap;

    /**
     * 当前对话的活动性总结, 拥有比dialogMap更丰富的全文细节, 作为当前对话token超限时的必要上下文压缩存储
     */
//    private List<String> currentCompressedSessionContext;

    /**
     * 存储确定性记忆, 如'用户爱好'等确定性信息
     * 该部分作为'主LLM'system prompt常驻
     */
    private HashMap<String /*userId*/, ConcurrentHashMap<String /*memoryKey*/, String /*memoryValue*/>> staticMemory;

    /**
     * memorySliceCache计数器，每日清空
     */
    private ConcurrentHashMap<List<String> /*触发查询的主题列表*/, Integer> memoryNodeCacheCounter;

    /**
     * 记忆切片缓存，每日清空
     * 用于记录作为终点节点调用次数最多的记忆节点的切片数据
     */
    private ConcurrentHashMap<List<String> /*主题路径*/, MemoryResult /*切片列表*/> memorySliceCache;

    /**
     * 缓存日期
     */
    private LocalDate cacheDate;

    /**
     * 智能体涉及到的各个模块中模型的prompt
     */
    private HashMap<String, String> modelPrompt;

    private String character;

    /**
     * 主模型的聊天记录
     */
    private List<Message> chatMessages;

    /**
     * 用户列表
     */
    private List<User> users;

    /**
     * 已被选中的切片时间戳集合，需要及时清理
     */
    private Set<Long> selectedSlices;

    private String memoryId;

    public MemoryGraph(String id) {
        this.id = id;
        this.topicNodes = new HashMap<>();
        this.existedTopics = new HashMap<>();
        this.dateIndex = new HashMap<>();
        this.staticMemory = new HashMap<>();
        this.memoryNodeCacheCounter = new ConcurrentHashMap<>();
        this.memorySliceCache = new ConcurrentHashMap<>();
        this.modelPrompt = new HashMap<>();
        this.selectedSlices = new HashSet<>();
        this.users = new ArrayList<>();
        this.userDialogMap = new ConcurrentHashMap<>();
//        this.currentCompressedSessionContext = new ArrayList<>();
        this.dialogMap = new HashMap<>();
    }

    public static MemoryGraph getInstance(String id) throws IOException, ClassNotFoundException {
        // 检查存储目录是否存在，不存在则创建
        createStorageDirectory();
        if (memoryGraph == null) {
            Path filePath = getFilePath(id);
            if (Files.exists(filePath)) {
                memoryGraph = deserialize(id);
            } else {
                FileUtils.createParentDirectories(filePath.toFile().getParentFile());
                memoryGraph = new MemoryGraph(id);
                memoryGraph.serialize();
            }
            log.info("MemoryGraph注册完毕...");
        }

        return memoryGraph;
    }

    public void serialize() throws IOException {
        Path filePath = getFilePath(this.id);
        Files.createDirectories(Path.of(STORAGE_DIR));
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(filePath.toFile()))) {
            oos.writeObject(this);
            log.info("MemoryGraph 已保存到: {}", filePath);
        } catch (IOException e) {
            log.error("序列化保存失败: {}", e.getMessage());
        }
    }

    private static MemoryGraph deserialize(String id) throws IOException, ClassNotFoundException {
        Path filePath = getFilePath(id);

        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(filePath.toFile()))) {
            MemoryGraph graph = (MemoryGraph) ois.readObject();
            log.info("MemoryGraph 已从文件加载: {}", filePath);
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

    public void insertMemory(List<String> topicPath, MemorySlice slice) throws IOException, ClassNotFoundException {
        //每日刷新缓存
        checkCacheDate();
        //如果topicPath在memorySliceCache中存在对应缓存，由于进行的插入操作，则需要移除该缓存，但不清除相关计数
        memorySliceCache.remove(topicPath);
        TopicNode lastTopicNode = generateTopicPath(topicPath);

        //检查是否存在当天对应的memorySlice并确定是否插入
        LocalDate now = LocalDate.now();
        boolean hasSlice = false;
        MemoryNode node = null;
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

        updateDateIndex(now, slice);
        if (!slice.isPrivate()) {
            updateUserDialogMap(slice);
        }
        node.saveMemorySliceList();
    }

    private TopicNode generateTopicPath(List<String> topicPath) {
        topicPath = new ArrayList<>(topicPath);
        //查看是否存在根主题节点
        String rootTopic = topicPath.getFirst();
        topicPath.removeFirst();
        if (!topicNodes.containsKey(rootTopic)) {
            TopicNode rootNode = new TopicNode();
            rootNode.setMemoryNodes(new CopyOnWriteArrayList<>());
            rootNode.setTopicNodes(new ConcurrentHashMap<>());
            topicNodes.put(rootTopic, rootNode);
            existedTopics.put(rootTopic, new LinkedHashSet<>());
        }

        TopicNode lastTopicNode = topicNodes.get(rootTopic);
        Set<String> existedTopicNodes = existedTopics.get(rootTopic);
        for (String topic : topicPath) {
            if (existedTopicNodes.contains(topic)) {
                lastTopicNode = lastTopicNode.getTopicNodes().get(topic);
            } else {
                TopicNode newNode = new TopicNode();
                lastTopicNode.getTopicNodes().put(topic, newNode);
                lastTopicNode = newNode;
                CopyOnWriteArrayList<MemoryNode> nodeList = new CopyOnWriteArrayList<>();
                lastTopicNode.setMemoryNodes(nodeList);
                lastTopicNode.setTopicNodes(new ConcurrentHashMap<>());
                existedTopicNodes.add(topic);
            }
        }
        return lastTopicNode;
    }

    private void updateUserDialogMap(MemorySlice slice) {
        String summary = slice.getSummary();
        LocalDateTime now = LocalDateTime.now();

        //更新userDialogMap
        //移除两天前上下文缓存(切片总结)
        List<LocalDateTime> keysToRemove = new ArrayList<>();
        userDialogMap.forEach((k, v) -> {
            v.forEach((i, j) -> {
                if (now.minusDays(2).isAfter(i)) {
                    keysToRemove.add(i);
                }
            });
        });
        for (LocalDateTime dateTime : keysToRemove) {
            userDialogMap.forEach((k, v) -> {
                v.remove(dateTime);
            });
        }
        //放入新缓存
        userDialogMap
                .computeIfAbsent(slice.getStartUserId(), k -> new ConcurrentHashMap<>())
                .merge(now, summary, (oldVal, newVal) -> oldVal + " " + newVal);

    }

    private void updateDateIndex(LocalDate now, MemorySlice slice) {
        String memoryId = slice.getMemoryId();
        //查看是否存在当前日期的对话切片索引
        if (!dateIndex.containsKey(now)) {
            dateIndex.put(now, new HashMap<>());
        }
        //查看当前日期的索引中是否存在该对话的索引
        HashMap<String, List<MemorySlice>> currentDateDialogSlices = dateIndex.get(now);
        if (!currentDateDialogSlices.containsKey(memoryId)) {
            List<MemorySlice> memorySliceList = new ArrayList<>();
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

    public MemoryResult selectMemory(String topicPathStr) throws IOException, ClassNotFoundException {
        List<String> topicPath = List.of(topicPathStr.split("->"));
        MemoryResult memoryResult = new MemoryResult();

        //每日刷新缓存
        checkCacheDate();
        //检测缓存并更新计数, 查看是否需要放入缓存
        updateCacheCounter(topicPath);
        //查看是否存在缓存，如果存在，则直接返回
        if (memorySliceCache.containsKey(topicPath)) {
            return memorySliceCache.get(topicPath);
        }
        CopyOnWriteArrayList<MemorySliceResult> targetSliceList = new CopyOnWriteArrayList<>();
        topicPath = new ArrayList<>(topicPath);
        String targetTopic = topicPath.getLast();
        TopicNode targetParentNode = getTargetParentNode(topicPath, targetTopic);
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

        //尝试更新缓存
        updateCache(topicPath, memoryResult);
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

    private void updateCache(List<String> topicPath, MemoryResult memoryResult) {
        Integer tempCount = memoryNodeCacheCounter.get(topicPath);
        if (tempCount >= 5) {
            memorySliceCache.put(topicPath, memoryResult);
        }
    }

    private void updateCacheCounter(List<String> topicPath) {
        if (memoryNodeCacheCounter.containsKey(topicPath)) {
            Integer tempCount = memoryNodeCacheCounter.get(topicPath);
            memoryNodeCacheCounter.put(topicPath, ++tempCount);
        } else {
            memoryNodeCacheCounter.put(topicPath, 1);
        }
    }

    private void checkCacheDate() {
        if (cacheDate.isBefore(LocalDate.now())) {
            memorySliceCache.clear();
            memoryNodeCacheCounter.clear();
        }
    }

    public MemoryResult selectMemory(LocalDate date) {
        MemoryResult memoryResult = new MemoryResult();
        CopyOnWriteArrayList<MemorySliceResult> targetSliceList = new CopyOnWriteArrayList<>();
        for (List<MemorySlice> value : dateIndex.get(date).values()) {
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
        return memoryResult;
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

        //逐层查找目标主题，可选取终点主题节点相邻位置的主题节点。终点记忆节点选取全部memoryNode, 邻近记忆节点选取最新日期的memoryNode
        while (!targetParentNode.getTopicNodes().containsKey(targetTopic)) {
            targetParentNode = targetParentNode.getTopicNodes().get(topicPath.getFirst());
            topicPath.removeFirst();
        }
        return targetParentNode;
    }

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

    private void printSubTopicsTreeFormat(TopicNode node, String prefix, StringBuilder stringBuilder) {
        if (node.getTopicNodes() == null || node.getTopicNodes().isEmpty()) return;

        List<Map.Entry<String, TopicNode>> entries = new ArrayList<>(node.getTopicNodes().entrySet());
        for (int i = 0; i < entries.size(); i++) {
            boolean last = (i == entries.size() - 1);
            Map.Entry<String, TopicNode> entry = entries.get(i);
            stringBuilder.append(prefix).append(last ? "└── " : "├── ").append(entry.getKey()).append("\r\n");
            printSubTopicsTreeFormat(entry.getValue(), prefix + (last ? "    " : "│   "), stringBuilder);
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
}

