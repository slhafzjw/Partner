package memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import work.slhaf.memory.MemoryGraph;
import work.slhaf.memory.content.MemorySlice;
import work.slhaf.memory.exception.UnExistedTopicException;
import work.slhaf.memory.node.MemoryNode;
import work.slhaf.memory.node.TopicNode;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchTest {
    private MemoryGraph memoryGraph;
    private final LocalDate today = LocalDate.now();
    private final LocalDate yesterday = LocalDate.now().minusDays(1);

    // 初始化测试环境，模拟插入基础数据
    @BeforeEach
    void setUp() throws IOException, ClassNotFoundException {
        memoryGraph = new MemoryGraph("testGraph");

        // 构建基础主题路径：根主题 -> 编程 -> Java
        List<String> javaPath = new ArrayList<>();
        javaPath.add("编程");
        javaPath.add("Java");

        // 插入今天的Java相关记忆
        MemorySlice javaMemory = createMemorySlice("java1");
        memoryGraph.insertMemory(javaPath, javaMemory);

        // 插入昨天的Java记忆（应不会出现在邻近结果中）
        MemorySlice oldJavaMemory = createMemorySlice("javaOld");
        MemoryNode oldNode = new MemoryNode();
        oldNode.setLocalDate(yesterday);
        oldNode.setMemorySliceList(List.of(oldJavaMemory));
    }

    // 场景1：查询存在的完整主题路径（含相关主题）
    @Test
    void selectMemory_shouldReturnTargetAndRelatedAndParentMemories() throws IOException, ClassNotFoundException {
        // 准备相关主题数据：根主题 -> 算法 -> 排序
        List<String> sortPath = new ArrayList<>();
        sortPath.add("算法");
        sortPath.add("排序");
        MemorySlice sortMemory = createMemorySlice("sort1");
        sortMemory.setRelatedTopics(List.of(
                createTopicPath("编程", "Java")  // 设置反向关联
        ));
        memoryGraph.insertMemory(sortPath, sortMemory);

        // 执行查询：编程 -> Java
        List<String> queryPath = new ArrayList<>();
        queryPath.add("算法");
        queryPath.add("排序");
        List<MemorySlice> results = memoryGraph.selectMemoryByPath(queryPath);

        // 验证结果应包含：
        // 1. 目标节点所有记忆（java1）
        // 2. 相关主题（排序）的最新记忆（sort1）
        // 3. 父节点（编程）的最新记忆（需要提前插入）
        assertTrue(results.stream().anyMatch(m -> "java1".equals(m.getMemoryId())));
        assertTrue(results.stream().anyMatch(m -> "sort1".equals(m.getMemoryId())));
        assertEquals(2, results.size()); // 根据具体实现可能调整
    }

    // 场景2：查询不存在的主题路径
    @Test
    void selectMemory_shouldThrowWhenPathNotExist() {
        List<String> invalidPath = new ArrayList<>();
        invalidPath.add("不存在的主题");

        assertThrows(UnExistedTopicException.class, () -> {
            memoryGraph.selectMemoryByPath(invalidPath);
        });
    }

    // 场景3：无相关主题时仅返回目标节点和父节点记忆
    @Test
    void selectMemory_withoutRelatedTopics_shouldReturnTargetAndParent() throws IOException, ClassNotFoundException {
        // 插入父级记忆：根主题 -> 编程
        List<String> parentPath = new ArrayList<>();
        parentPath.add("编程");
        MemorySlice parentMemory = createMemorySlice("parent1");
        memoryGraph.insertMemory(parentPath, parentMemory);

        // 执行查询
        List<String> queryPath = new ArrayList<>();
        queryPath.add("编程");
        queryPath.add("Java");
        List<MemorySlice> results = memoryGraph.selectMemoryByPath(queryPath);

        // 应包含：Java记忆 + 父级最新记忆
        assertTrue(results.stream().anyMatch(m -> "java1".equals(m.getMemoryId())));
        assertTrue(results.stream().anyMatch(m -> "parent1".equals(m.getMemoryId())));
        assertEquals(2, results.size());
    }

    // 场景4：验证日期排序，应优先取最新日期的邻近记忆
    @Test
    void selectMemory_shouldGetLatestRelatedMemory() throws IOException, ClassNotFoundException {
        // 准备相关主题路径：根主题 -> 数据库
        List<String> dbPath = new ArrayList<>();
        dbPath.add("数据库");
        dbPath.add("mysql");

        // 插入今天的数据库记忆（正常流程）
        MemorySlice newDbMemory = createMemorySlice("dbNew");
        memoryGraph.insertMemory(dbPath, newDbMemory);

        // 手动构建并插入昨天的数据库记忆
        MemorySlice oldDbMemory = createMemorySlice("dbOld");
        TopicNode dbTopicNode = memoryGraph.getTopicNodes().get("数据库");

        // 创建昨日记忆节点并添加到主题节点
        MemoryNode oldMemoryNode = new MemoryNode();
        oldMemoryNode.setLocalDate(yesterday);
        oldMemoryNode.setMemorySliceList(new ArrayList<>(List.of(oldDbMemory)));
        dbTopicNode.getMemoryNodes().add(oldMemoryNode);

        // 对记忆节点进行日期排序（根据compareTo方法）
        dbTopicNode.getMemoryNodes().sort(null);

        // 创建Java记忆并关联数据库主题
        MemorySlice javaMemory = createMemorySlice("java2");
        javaMemory.setRelatedTopics(List.of(
                createTopicPath("数据库","") // 完整主题路径
        ));
        memoryGraph.insertMemory(createTopicPath("编程", "Java"), javaMemory);

        // 执行查询
        List<String> queryPath = createTopicPath("编程", "Java");
        List<MemorySlice> results = memoryGraph.selectMemoryByPath(queryPath);

        // 验证结果应包含最新关联记忆（dbNew）
        assertTrue(results.stream().anyMatch(m -> "dbNew".equals(m.getMemoryId())),
                "应包含最新的数据库记忆");
        assertFalse(results.stream().anyMatch(m -> "dbOld".equals(m.getMemoryId())),
                "不应包含过期的数据库记忆");

        // 验证结果包含目标记忆（java1和java2）
        assertTrue(results.stream().anyMatch(m -> "java1".equals(m.getMemoryId())),
                "应包含基础测试数据");
        assertTrue(results.stream().anyMatch(m -> "java2".equals(m.getMemoryId())),
                "应包含当前测试插入数据");
    }

    private MemorySlice createMemorySlice(String id) {
        MemorySlice slice = new MemorySlice();
        slice.setMemoryId(id);
        return slice;
    }

    private ArrayList<String> createTopicPath(String... topics) {
        ArrayList<String> path = new ArrayList<>();
        for (String topic : topics) {
            path.add(topic);
        }
        return path;
    }
}