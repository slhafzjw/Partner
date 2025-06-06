package work.slhaf.agent.core.memory.submodule.graph;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.serialize.PersistableObject;
import work.slhaf.agent.core.memory.exception.UnExistedDateIndexException;
import work.slhaf.agent.core.memory.exception.UnExistedTopicException;
import work.slhaf.agent.core.memory.pojo.MemoryResult;
import work.slhaf.agent.core.memory.pojo.MemorySliceResult;
import work.slhaf.agent.core.memory.submodule.graph.pojo.MemorySlice;
import work.slhaf.agent.core.memory.submodule.graph.pojo.node.MemoryNode;
import work.slhaf.agent.core.memory.submodule.graph.pojo.node.TopicNode;

import java.io.IOException;
import java.io.Serial;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@EqualsAndHashCode(callSuper = true)
@Data
public class GraphCore extends PersistableObject {

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
        return memoryResult;
    }

    private List<List<MemorySlice>> loadSlicesByDate(LocalDate date) throws IOException, ClassNotFoundException {
        if (!dateIndex.containsKey(date)) {
            throw new UnExistedDateIndexException("不存在的日期索引: " + date);
        }
        List<List<MemorySlice>> list = new ArrayList<>();
        for (String memoryId : dateIndex.get(date)) {
            MemoryNode memoryNode = new MemoryNode();
            memoryNode.setMemoryNodeId(memoryId);
            list.add(memoryNode.loadMemorySliceList());
        }
        return list;
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
            stringBuilder.append(prefix).append(last ? "└── " : "├── ").append(entry.getKey()).append("[").append(entry.getValue().getMemoryNodes().size()).append("]").append("\r\n");
            printSubTopicsTreeFormat(entry.getValue(), prefix + (last ? "    " : "│   "), stringBuilder);
        }
    }

    public void insertMemory(List<String> topicPath, MemorySlice slice) throws IOException, ClassNotFoundException {
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

        node.saveMemorySliceList();

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

}
