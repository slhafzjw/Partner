package memory;

import org.junit.jupiter.api.Test;
import work.slhaf.agent.common.chat.ChatClient;
import work.slhaf.agent.common.chat.constant.ChatConstant;
import work.slhaf.agent.common.chat.pojo.Message;

import java.util.ArrayList;
import java.util.List;

public class AITest {
    @Test
    public void test1(){
        ChatClient client = new ChatClient("https://open.bigmodel.cn/api/paas/v4/chat/completions","3db444552530b7742b0c53425fb93dcc.LcVwYjByht9AC3N9","glm-4-flash");
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(ChatConstant.Character.SYSTEM, """
                # MemorySelectExtractor 提示词
                
                ## 功能说明
                你需要根据用户输入的JSON数据，分析其`text`字段内容，判断是否需要通过主题路径或日期进行记忆查询，并返回标准化格式的JSON响应。
                
                ## 输入字段说明
                - `text`: 用户输入的文本内容
                - `topic_tree`: 当前可用的主题树结构（括号内数字表示子主题数量）
                - `date`: 当前对话发生的日期（用于时间推理）
                
                ## 输出规则
                1. 当文本涉及明确主题路径时：
                   - 使用`"type": "topic"`
                   - `text`字段格式为"根主题->子主题->子子主题"（必须**完全匹配**topic_tree中的层级，包括从[root]到目标主题的完整路径）
                   - 示例：{
                     "type": "topic",
                     "text": "工作->项目A->需求文档"
                   }
                
                2. 当文本包含明确可推算的日期时：
                   - 使用`"type": "date"`
                   - 日期格式必须为"YYYY-MM-DD"
                   - 仅接受具体日期（不接受"上周"等模糊表达）
                   - 示例：{
                     "type": "date",
                     "text": "2024-04-15"
                   }
                
                3. 当不需要查询或无法确定时：
                   - 使用`"type": "none"`
                   - 示例：{
                     "type": "none"
                   }
                
                ## 完整示例
                用户输入：{
                  "text": "还记得我们讨论过游戏引擎的物理系统实现吗？",
                  "topic_tree": "
                 技术 (3)[root]
                ├── 游戏开发 (2)
                │   ├── 图形渲染 (1)
                │   └── 物理系统 (0)
                └── 人工智能 (1)",
                  "date": "2024-04-20"
                }
                
                正确响应：{
                  "type": "topic",
                  "text": "技术->游戏开发->物理系统"
                }
                """));

        messages.add(new Message(ChatConstant.Character.USER, """
                {
                  "text": "上周似乎发生了什么重要的事？？",
                  "topic_tree": "
                汽车工程 (4)[root]
                ├── 动力系统 (3)
                │   ├── 发动机 (1)
                │   └── 新能源电池 (2)
                │       ├── 测试标准 (1)
                │       └── 安全规范 (1)
                └── 车身设计 (1)
                软件开发 (3)[root]
                质量管理 (2)[root]
                ├── ISO认证 (1)
                └── 行业标准 (1)",
                  "date": "2024-04-20"
                }
                """));
        System.out.println(client.runChat(messages).getMessage());
    }
}
