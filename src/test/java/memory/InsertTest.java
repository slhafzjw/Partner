package memory;

import work.slhaf.agent.core.memory.MemoryGraph;
import work.slhaf.agent.core.memory.node.MemoryNode;
import work.slhaf.agent.core.memory.node.TopicNode;
import work.slhaf.agent.core.memory.pojo.MemorySlice;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

public class InsertTest {
    private MemoryGraph memoryGraph;
    private final String testId = "test_insert";
    String basicCharacter = "";

//    @Before
    public void setUp() {
        memoryGraph = new MemoryGraph(testId, basicCharacter);
        memoryGraph.setTopicNodes(new HashMap<>());
        memoryGraph.setExistedTopics(new HashMap<>());
    }

//    @Test
    public void testInsertMemory_NewRootTopic() throws IOException, ClassNotFoundException {
        // 准备测试数据
        List<String> topicPath = new LinkedList<>(Arrays.asList("Programming", "Java", "Collections"));
        MemorySlice slice = createTestMemorySlice("slice1");

        // 执行测试
        memoryGraph.insertMemory(topicPath, slice);

        // 验证结果
        assertTrue(memoryGraph.getTopicNodes().containsKey("Programming"));
        TopicNode programmingNode = memoryGraph.getTopicNodes().get("Programming");

        assertTrue(programmingNode.getTopicNodes().containsKey("Java"));
        TopicNode javaNode = programmingNode.getTopicNodes().get("Java");

        assertTrue(javaNode.getTopicNodes().containsKey("Collections"));
        TopicNode collectionsNode = javaNode.getTopicNodes().get("Collections");

        assertEquals(1, collectionsNode.getMemoryNodes().size());
        MemoryNode memoryNode = collectionsNode.getMemoryNodes().get(0);
        assertEquals(LocalDate.now(), memoryNode.getLocalDate());
        assertEquals(1, memoryNode.loadMemorySliceList().size());
        assertEquals(slice, memoryNode.loadMemorySliceList().get(0));
    }

//    @Test
    public void testInsertMemory_ExistingTopicPath() throws IOException, ClassNotFoundException {
        // 准备初始数据
        List<String> topicPath1 = new LinkedList<>(Arrays.asList("Programming", "Java", "Collections"));
        MemorySlice slice1 = createTestMemorySlice("slice1");
        memoryGraph.insertMemory(topicPath1, slice1);

        // 插入第二个记忆片段到相同路径
        List<String> topicPath2 = new LinkedList<>(Arrays.asList("Programming", "Java", "Collections"));
        MemorySlice slice2 = createTestMemorySlice("slice2");
        memoryGraph.insertMemory(topicPath2, slice2);

        // 验证结果
        TopicNode collectionsNode = memoryGraph.getTopicNodes().get("Programming")
                .getTopicNodes().get("Java")
                .getTopicNodes().get("Collections");

        assertEquals(1, collectionsNode.getMemoryNodes().size()); // 同一天应该只有一个MemoryNode
        assertEquals(2, collectionsNode.getMemoryNodes().get(0).loadMemorySliceList().size()); // 但有两个MemorySlice
    }

//    @Test
    public void testInsertMemory_DifferentDays() throws IOException, ClassNotFoundException {
        // 准备测试数据
        List<String> topicPath = new LinkedList<>(Arrays.asList("Math", "Algebra"));
        MemorySlice slice1 = createTestMemorySlice("slice1");
        MemorySlice slice2 = createTestMemorySlice("slice2");

        // 第一次插入
        memoryGraph.insertMemory(topicPath, slice1);

        // 模拟第二天
        MemoryNode firstNode = memoryGraph.getTopicNodes().get("Math")
                .getTopicNodes().get("Algebra")
                .getMemoryNodes().get(0);
        firstNode.setLocalDate(LocalDate.now().minusDays(1));

        // 第二次插入
        memoryGraph.insertMemory(topicPath, slice2);

        // 验证结果
        TopicNode algebraNode = memoryGraph.getTopicNodes().get("Math")
                .getTopicNodes().get("Algebra");

        assertEquals(2, algebraNode.getMemoryNodes().size()); // 应该有两个MemoryNode
    }

//    @Test
    public void testInsertMemory_PartialExistingPath() throws IOException, ClassNotFoundException {
        // 准备初始数据 - 创建部分路径
        List<String> topicPath1 = new LinkedList<>(Arrays.asList("Science", "Physics"));
        MemorySlice slice1 = createTestMemorySlice("slice1");
        memoryGraph.insertMemory(topicPath1, slice1);

        // 插入到已存在路径的扩展路径
        List<String> topicPath2 = new LinkedList<>(Arrays.asList("Science", "Physics", "Mechanics"));
        MemorySlice slice2 = createTestMemorySlice("slice2");
        memoryGraph.insertMemory(topicPath2, slice2);

        // 验证结果
        TopicNode physicsNode = memoryGraph.getTopicNodes().get("Science")
                .getTopicNodes().get("Physics");

        assertTrue(physicsNode.getTopicNodes().containsKey("Mechanics"));
        assertEquals(1, physicsNode.getMemoryNodes().size()); // Physics节点有自己的记忆
        assertEquals(1, physicsNode.getTopicNodes().get("Mechanics").getMemoryNodes().size()); // Mechanics节点也有记忆
    }

    private MemorySlice createTestMemorySlice(String id) {
        MemorySlice slice = new MemorySlice();
        slice.setMemoryId(id);
        // 可以设置其他必要属性
        return slice;
    }

//    @Test
    public void testSerializationConsistency() throws IOException, ClassNotFoundException {
        // 构造 MemorySlice
        MemorySlice slice = new MemorySlice();
        slice.setMemoryId("001");

        List<String> topicPath = Arrays.asList("生活", "学习", "Java");

        // 插入 memory
        memoryGraph.insertMemory(topicPath, slice);
        memoryGraph.serialize();

        // 反序列化
        MemoryGraph loadedGraph = MemoryGraph.getInstance(testId, "");

        // 校验：topic 是否存在
        assertNotNull(loadedGraph.getTopicNodes().get("生活"));
        TopicNode lifeNode = loadedGraph.getTopicNodes().get("生活");

        assertNotNull(lifeNode.getTopicNodes().get("学习"));
        TopicNode studyNode = lifeNode.getTopicNodes().get("学习");

        assertNotNull(studyNode.getTopicNodes().get("Java"));
        TopicNode javaNode = studyNode.getTopicNodes().get("Java");

        // 校验：是否存在 MemoryNode
        assertFalse(javaNode.getMemoryNodes().isEmpty());

        // 校验：MemorySlice 内容一致
        MemorySlice deserializedSlice = javaNode.getMemoryNodes().get(0).loadMemorySliceList().get(0);
        assertEquals("001", deserializedSlice.getMemoryId());
    }

}
