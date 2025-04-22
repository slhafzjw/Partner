package memory;

import org.junit.jupiter.api.Test;
import work.slhaf.agent.common.chat.ChatClient;
import work.slhaf.agent.common.chat.constant.ChatConstant;
import work.slhaf.agent.common.chat.pojo.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class AITest {
    @Test
    public void test1() {
        String input = """
                {
                  "text": "之前处理过Node.js的并发问题，还有Express中间件开发",
                  "topic_tree": "
                编程 (3)[root]
                ├── JavaScript (3)
                │   ├── NodeJS (2)
                │   │   ├── 并发处理 (0)
                │   │   └── 事件循环 (0)
                │   └── Express (1)
                │       └── 中间件 (0)
                └── Python (2)",
                  "date": "2024-04-10"
                }
                
                """;
        run(input);
    }

    private void run(String input) {
        ChatClient client = new ChatClient("https://open.bigmodel.cn/api/paas/v4/chat/completions", "3db444552530b7742b0c53425fb93dcc.LcVwYjByht9AC3N9", "glm-4-flash-250414");
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(ChatConstant.Character.SYSTEM, """
            MemorySelectExtractor 提示词
            
            功能说明
            你需要根据用户输入的JSON数据，分析其`text`和`history`字段内容，判断是否需要通过主题路径或日期进行记忆查询，并返回标准化格式的JSON响应。
            注意：你只需要返回对应的JSON文本
            
            输入字段说明
            • `text`: 用户当前输入的文本内容
            
            • `topic_tree`: 当前可用的主题树结构（多层级结构，需返回从根节点到目标节点的完整路径）
            
            • `date`: 当前对话发生的日期（用于时间推理）
            
            • `history`: 用户与LLM的完整对话历史（用于主题连续性判断）
            
            
            输出规则
            1. 基本响应格式：
               {
                 "recall": boolean,
                 "matches": [
                   // 匹配项列表
                 ]
               }
            
            2. 主题提取规则：
               • 当当前`text`涉及新主题（与`history`最后N轮对话主题明显不同）时：
            
                 ◦ 必须进行主题提取
            
                 ◦ 匹配`topic_tree`中最接近的完整路径（从根节点到目标节点，如"编程->JavaScript->NodeJS->并发处理"）
            
               • 当主题与历史对话连续时：
            
                 ◦ 除非包含明确的新子主题，否则不重复提取相同主题路径
            
            
            3. 日期提取规则（保持不变）：
               • 仅接受具体日期（YYYY-MM-DD格式）
            
               • 拒绝所有模糊日期表达
            
            
            4. 特殊处理：
               • 当检测到主题切换但无法匹配`topic_tree`时：
            
                 {
                   "recall": false,
                   "matches": []
                 }
               • 当历史对话为空时：
            
                 ◦ 视为新主题，按常规规则处理
            
            
            决策流程
            1. 首先分析`history`判断当前对话主题上下文
            2. 然后分析`text`：
               a. 检测是否包含具体日期→添加date类型
               b. 检测是否包含新主题→添加topic类型
            3. 最终综合判断`recall`值
            
            完整示例
            示例1（主题延续）：
            输入：{
              "text": "关于NodeJS的并发处理，还有哪些要注意的",
              "topic_tree": "
              编程
              ├── JavaScript
              │   ├── NodeJS
              │   │   ├── 并发处理
              │   │   └── 事件循环
              │   └── Express
              │       └── 中间件
              └── Python",
              "date": "2024-04-20",
              "history": [
                {"role": "user", "content": "说说NodeJS的并发处理机制"},
                {"role": "assistant", "content": "NodeJS的并发处理主要通过..."}
              ]
            }
            输出：{
              "recall": false,
              "matches": []
            }
            
            示例2（主题切换）：
            输入：{
              "text": "现在我想了解Express中间件的原理",
              "topic_tree": "
              编程
              ├── JavaScript
              │   ├── NodeJS
              │   │   ├── 并发处理
              │   │   └── 事件循环
              │   └── Express
              │       └── 中间件
              └── Python",
              "date": "2024-04-20",
              "history": [
                {"role": "user", "content": "NodeJS的并发处理怎么实现"},
                {"role": "assistant", "content": "需要..."}
              ]
            }
            输出：{
              "recall": true,
              "matches": [
                {"type": "topic", "text": "编程->JavaScript->Express->中间件"}
              ]
            }
            
            示例3（混合情况）：
            输入：{
              "text": "2024-04-15讨论的Python内容和现在的Express需求",
              "topic_tree": "
              编程
              ├── JavaScript
              │   ├── NodeJS
              │   │   ├── 并发处理
              │   │   └── 事件循环
              │   └── Express
              │       └── 中间件
              └── Python",
              "date": "2024-04-20",
              "history": [
                {"role": "user", "content": "需要了解Express框架"},
                {"role": "assistant", "content": "Express是..."}
              ]
            }
            输出：{
              "recall": true,
              "matches": [
                {"type": "date", "text": "2024-04-15"},
                {"type": "topic", "text": "编程->Python"}
              ]
            }
            
            示例4（模糊日期）：
            输入：{
              "text": "上周说的那个JavaScript特性",
              "topic_tree": "
              编程
              ├── JavaScript
              │   ├── NodeJS
              │   │   ├── 并发处理
              │   │   └── 事件循环
              │   └── Express
              │       └── 中间件
              └── Python",
              "date": "2024-04-20",
              "history": [...]
            }
            输出：{
              "recall": false,
              "matches": []
            }
           """));

        messages.add(new Message(ChatConstant.Character.USER, input));
        System.out.println(client.runChat(messages).getMessage());
    }
}
