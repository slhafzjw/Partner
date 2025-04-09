package work.slhaf.memory;

import lombok.Data;
import work.slhaf.memory.content.MemorySlice;
import work.slhaf.memory.exception.UnExistedTopicException;
import work.slhaf.memory.node.MemoryNode;
import work.slhaf.memory.node.TopicNode;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

@Data
public class MemoryGraph implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final String STORAGE_DIR = "./data/memory/";

    private String id;
    private HashMap<String, TopicNode> topicNodes;
    public static MemoryGraph memoryGraph;
    private HashMap<String, Set<String>> existedTopics;

    public MemoryGraph(String id) {
        this.id = id;
        this.topicNodes = new HashMap<>();
        this.existedTopics = new HashMap<>();
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
            System.out.println("MemoryGraph 已从文件加载: " + filePath);
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

    public void insertMemory(List<String> topicPath, MemorySlice slice) {
        topicPath = new ArrayList<>(topicPath);
        if (topicNodes == null) {
            topicNodes = new HashMap<>();
        }
        //查看是否存在根主题节点
        String rootTopic = topicPath.getFirst();
        topicPath.removeFirst();
        if (!topicNodes.containsKey(rootTopic)) {
            TopicNode rootNode = new TopicNode();
            rootNode.setMemoryNodes(new ArrayList<>());
            rootNode.setTopicNodes(new HashMap<>());
            topicNodes.put(rootTopic, rootNode);
            existedTopics.put(rootTopic, new HashSet<>());
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
                /*if (i == topicPath.size() - 1) {
                    lastTopicNode.setMemoryNodes(new ArrayList<>());
                    lastTopicNode.setTopicNodes(new HashMap<>());
                }*/
            }
        }
        //检查是否存在当天对应的memoryData
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
    }

    public List<MemorySlice> selectMemory(List<String> topicPath) {
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

    private TopicNode getTargetParentNode(List<String> topicPath, String targetTopic) {
        String topTopic = topicPath.getFirst();
        if (!existedTopics.containsKey(topTopic)){
            throw new UnExistedTopicException("不存在的主题: " + topTopic);
        }
        TopicNode targetParentNode = topicNodes.get(topTopic);
        topicPath.removeFirst();
        for (String topic : topicPath) {
            if (!existedTopics.get(topTopic).contains(topic)){
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

