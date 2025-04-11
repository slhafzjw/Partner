package work.slhaf.memory;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.memory.content.MemorySlice;
import work.slhaf.memory.exception.UnExistedTopicException;
import work.slhaf.memory.node.MemoryNode;
import work.slhaf.memory.node.TopicNode;
import work.slhaf.memory.pojo.PersistableObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class MemoryGraph extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String STORAGE_DIR = "./data/memory/";
    //todo: 实现记忆的短期缓存机制
    private String id;
    /**
     * key: 根主题名称  value: 根主题节点
     */
    private HashMap<String, TopicNode> topicNodes;
    public static MemoryGraph memoryGraph;

    /**
     * 用于存储已存在的主题列表，便于记忆查找, 使用根主题名称作为键, 子主题名称集合为值
     * 该部分在'主题提取LLM'的system prompt中常驻
     */
    private HashMap<String, LinkedHashSet<String>> existedTopics;

    /**
     * 记忆节点的日期索引, 同一日期内按照对话id区分
     * 同时作为临时的同一对话切片容器, 用于为同一对话内的不同切片提供更新上下文的场所
     */
    private HashMap<LocalDate, HashMap<String, List<MemorySlice>>> dateIndex;

    /**
     * 近两日的对话总结缓存, 用于为大模型提供必要的记忆补充, hashmap以切片的存储时间为键，总结为值
     * 该部分作为'主LLM'system prompt常驻
     * 该部分作为近两日的整体对话缓存, 不区分用户
     */
    private HashMap<LocalDateTime, String> dialogMap;

    /**
     * 近两日的区分用户的对话总结缓存，在prompt结构上比dialogMap层级深一层, dialogMap更具近两日整体对话的摘要性质
     */
    private HashMap<LocalDateTime,HashMap<String/*userId*/,String>> userDialogMap;

    /**
     * 当前对话的活动性总结, 拥有比dialogMap更丰富的全文细节, 作为当前对话token超限时的必要上下文压缩存储
     */
    private List<String> currentCompressedSessionContext;

    /**
     * 存储确定性记忆, 如'用户爱好'等确定性信息
     * 该部分作为'主LLM'system prompt常驻
     */
    private HashMap<String /*userId*/, HashMap<String /*memoryKey*/,String /*memoryValue*/>> staticMemory;

    public MemoryGraph(String id) {
        this.id = id;
        this.topicNodes = new HashMap<>();
        this.existedTopics = new HashMap<>();
        this.dateIndex = new HashMap<>();
        this.staticMemory = new HashMap<>();
    }

    public static MemoryGraph initialize(String id) {
        // 检查存储目录是否存在，不存在则创建
        createStorageDirectory();

        Path filePath = getFilePath(id);

        if (Files.exists(filePath)) {
            try {
                // 从文件加载
                return deserialize(id);
            } catch (Exception e) {
                System.err.println("加载序列化文件失败，创建新实例: " + e.getMessage());
                return new MemoryGraph(id);
            }
        } else {
            // 创建新实例
            return new MemoryGraph(id);
        }
    }

    public void serialize() {
        Path filePath = getFilePath(this.id);

        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(filePath.toFile()))) {
            oos.writeObject(this);
            System.out.println("MemoryGraph 已保存到: " + filePath);
        } catch (IOException e) {
            System.err.println("序列化保存失败: " + e.getMessage());
        }
    }

    private static MemoryGraph deserialize(String id) throws IOException, ClassNotFoundException {
        Path filePath = getFilePath(id);

        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(filePath.toFile()))) {
            MemoryGraph graph = (MemoryGraph) ois.readObject();
            log.info("MemoryGraph 已从文件加载: " + filePath);
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
        topicPath = new ArrayList<>(topicPath);
        //查看是否存在根主题节点
        String rootTopic = topicPath.getFirst();
        topicPath.removeFirst();
        if (!topicNodes.containsKey(rootTopic)) {
            TopicNode rootNode = new TopicNode();
            rootNode.setMemoryNodes(new ArrayList<>());
            rootNode.setTopicNodes(new HashMap<>());
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
                List<MemoryNode> nodeList = new ArrayList<>();
                lastTopicNode.setMemoryNodes(nodeList);
                lastTopicNode.setTopicNodes(new HashMap<>());
                existedTopicNodes.add(topic);
            }
        }
        //检查是否存在当天对应的memorySlice
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
            node.setMemorySliceList(new ArrayList<>());
            lastTopicNode.getMemoryNodes().add(node);
            lastTopicNode.getMemoryNodes().sort(null);
        }
        node.getMemorySliceList().add(slice);

        updateDateIndex(now, slice);
        updateDialogMap(slice);
        node.saveMemorySliceList();
    }

    private void updateDialogMap(MemorySlice slice) {
        String summary = slice.getSummary();
        LocalDateTime now = LocalDateTime.now();
        //更新dialogMap
        //移除两天前的上下文缓存(切片总结)
        List<LocalDateTime> keysToRemove = new ArrayList<>();
        dialogMap.forEach((k, v) -> {
            if (now.minusDays(2).isAfter(k)){
                keysToRemove.add(k);
            }
        });
        for (LocalDateTime dateTime : keysToRemove) {
            dialogMap.remove(dateTime);
        }
        keysToRemove.clear();
        //放入新缓存
        dialogMap.put(now,summary);
        //更新userDialogMap
        //移除两天前上下文缓存(切片总结)
        userDialogMap.forEach((k,v) -> {
            if (now.minusDays(2).isAfter(k)){
                keysToRemove.add(k);
            }
        });
        for (LocalDateTime dateTime : keysToRemove) {
            userDialogMap.remove(dateTime);
        }
        //放入新缓存
        userDialogMap.get(now).put(slice.getStartUser(),slice.getSummary());
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
            //末尾切片添加当前切片的引用
            tempSlice.setSliceAfter(slice);
            //当前切片添加前序切片的引用
            slice.setSliceBefore(tempSlice);
        }

    }

    public List<MemorySlice> selectMemoryByPath(List<String> topicPath) throws IOException, ClassNotFoundException {
        List<MemorySlice> targetSliceList = new ArrayList<>();
        topicPath = new ArrayList<>(topicPath);
        String targetTopic = topicPath.getLast();
        TopicNode targetParentNode = getTargetParentNode(topicPath, targetTopic);
        List<List<String>> relatedTopics = new ArrayList<>();
        //终点记忆节点
        for (MemoryNode memoryNode : targetParentNode.getTopicNodes().get(targetTopic).getMemoryNodes()) {
            List<MemorySlice> endpointMemorySliceList = memoryNode.getMemorySliceList();
            targetSliceList.addAll(endpointMemorySliceList);
            for (MemorySlice memorySlice : endpointMemorySliceList) {
                if (memorySlice.getRelatedTopics() != null) {
                    relatedTopics.addAll(memorySlice.getRelatedTopics());
                }
            }
        }
        //邻近记忆节点 联系
        for (List<String> relatedTopic : relatedTopics) {
            List<String> tempTopicPath = new ArrayList<>(relatedTopic);
            String tempTargetTopic = tempTopicPath.getLast();
            TopicNode tempTargetParentNode = getTargetParentNode(tempTopicPath, tempTargetTopic);
            //获取终点节点及其最新记忆节点
            TopicNode tempTargetNode = tempTargetParentNode.getTopicNodes().get(tempTopicPath.getLast());
            List<MemoryNode> tempMemoryNodes = tempTargetNode.getMemoryNodes();
            if (!tempMemoryNodes.isEmpty()) {
                targetSliceList.addAll(tempMemoryNodes.getFirst().getMemorySliceList());
            }
        }
        //邻近记忆节点 父级
        List<MemoryNode> targetParentMemoryNodes = targetParentNode.getMemoryNodes();
        if (!targetParentMemoryNodes.isEmpty()) {
            targetSliceList.addAll(targetParentMemoryNodes.getFirst().getMemorySliceList());
        }
        return targetSliceList;
    }

    public HashMap<String,List<MemorySlice>> selectMemoryByDate(LocalDate date){
        return dateIndex.get(date);
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
}

