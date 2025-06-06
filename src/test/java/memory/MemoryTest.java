package memory;

import work.slhaf.agent.core.memory.MemoryGraph;
import work.slhaf.agent.core.memory.node.TopicNode;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryTest {

//@Test
public void test1() {
    String basicCharacter = "";
    MemoryGraph graph = new MemoryGraph("test");
    HashMap<String, TopicNode> topicMap = new HashMap<>();

    TopicNode root1 = new TopicNode();
    root1.setTopicNodes(new ConcurrentHashMap<>());

    TopicNode sub1 = new TopicNode();
    sub1.setTopicNodes(new ConcurrentHashMap<>());

    TopicNode sub2 = new TopicNode();
    sub2.setTopicNodes(new ConcurrentHashMap<>());

    TopicNode subsub1 = new TopicNode();
    subsub1.setTopicNodes(new ConcurrentHashMap<>());

    // 构造结构：root -> sub1 -> subsub1, root -> sub2
    sub1.getTopicNodes().put("子子主题1", subsub1);
    root1.getTopicNodes().put("子主题1", sub1);
    root1.getTopicNodes().put("子主题2", sub2);

    topicMap.put("根主题1", root1);

    // 添加 root2
    TopicNode root2 = new TopicNode();
    root2.setTopicNodes(new ConcurrentHashMap<>());

    TopicNode sub3 = new TopicNode();
    sub3.setTopicNodes(new ConcurrentHashMap<>());

    // 构造结构：root2 -> sub3
    root2.getTopicNodes().put("子主题3", sub3);

    topicMap.put("根主题2", root2);

    // 输出
    graph.setTopicNodes(topicMap);
    System.out.println(graph.getTopicTree());
}


//    @Test
    public void test2(){
        System.out.println(LocalDate.now());
    }

}
