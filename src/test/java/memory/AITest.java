package memory;

import cn.hutool.json.JSONUtil;
import work.slhaf.agent.common.chat.ChatClient;
import work.slhaf.agent.common.chat.constant.ChatConstant;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.model.ModelConstant;
import work.slhaf.agent.modules.memory.selector.MemorySelector;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AITest {
//    @Test
    public void topicExtractorTest() {
        String input = """
                {
                  "text": "回到昨天讨论的TypeScript装饰器实现AOP，结合现在需要的微服务熔断机制",
                  "topic_tree": "编程\\n├── JavaScript\\n│   ├── NodeJS\\n│   └── TypeScript\\n│       ├── 类型系统\\n│       ├── 装饰器\\n│       │   ├── 类装饰器\\n│       │   └── 方法装饰器\\n│       └── 编译配置\\n└── 系统设计\\n    ├── 微服务\\n    │   ├── 服务发现\\n    │   └── 熔断机制\\n    └── 消息队列",
                  "date": "2024-05-20",
                  "history": [
                    {
                      "role": "user",
                      "content": "我们的TS项目需要实现日志切面，装饰器方案和中间件方案哪个更合适？"
                    },
                    {
                      "role": "assistant",
                      "content": "两种方案各有优劣：装饰器的优势在于1) 声明式编程 2) 精确到方法级别 3) 元编程能力。中间件方案则更适合请求级别的处理。具体到实现细节，方法装饰器可以通过Reflect Metadata..."
                    },
                    {
                      "role": "user",
                      "content": "在微服务架构中，如何设计跨服务的统一日志收集？特别是Kubernetes环境下的实现"
                    },
                    {
                      "role": "assistant",
                      "content": "K8s环境下的日志方案需要考虑：1) DaemonSet部署Fluentd 2) 应用层的日志规范 3) EFK栈的索引策略。对于NodeJS应用，建议使用Winston配合..."\s
                    },
                    {
                      "role": "user",
                      "content": "现在遇到服务雪崩问题，需要实现熔断降级"
                    }
                  ]
                }
                """;
        run(input, ModelConstant.SELECT_EXTRACTOR_PROMPT);
    }

//    @Test
    public void sliceEvaluatorTest(){
        String input = """
                {
                  "text": "请结合我们之前讨论的美联储加息影响，分析下当前黄金ETF和国债逆回购的组合策略",
                  "history": [
                    {
                      "role": "user",
                      "content": "美联储连续加息对A股有什么影响？"
                    },
                    {
                      "role": "assistant",
                      "content": "历史数据分析显示：\\n1. 北上资金流动：利空流动性敏感板块\\n2. 汇率压力：增加出口企业汇兑收益\\n3. 行业分化：利好银行/出口，利空地产/消费\\n具体机制..."
                    },
                    {
                      "role": "user",
                      "content": "黄金作为避险资产现在可以配置吗？"
                    },
                    {
                      "role": "assistant",
                      "content": "黄金配置建议：\\n• 实际利率是核心影响因素\\n• 短期受美元指数压制\\n• 长期抗通胀属性仍在\\n建议比例..."
                    }
                  ],
                  "memory_slices": [
                    {
                      "summary": "美联储加息周期的大类资产配置策略研究，包含历史回测数据（2004-2006、2015-2018两次加息周期中黄金、美债、新兴市场股市的表现），以及当前特殊环境（高通胀+地缘冲突）下的策略调整建议。",
                      "id": 1685587200,
                      "date": "2025-06-20"
                    },
                    {
                      "summary": "黄金ETF与国债逆回购的组合优化模型，通过波动率分析给出了不同风险偏好下的最优配置比例，特别讨论了在流动性紧张时期如何利用逆回购对冲黄金波动。",
                      "id": 1685673600,
                      "date": "2025-06-21"
                    },
                    {
                      "summary": "A股行业轮动与美联储政策的相关性研究，建立了包含利率敏感度、外资持仓比例、出口依赖度等因子的分析框架，并给出了当前环境下的行业配置建议。",
                      "id": 1685760000,
                      "date": "2025-06-22"
                    },
                    {
                      "summary": "跨境资本流动监测指标体系详解，包含利差模型、风险偏好指标（VIX指数）、以及中国特有的资本管制有效性分析。",
                      "id": 1685846400,
                      "date": "2025-06-23"
                    }
                  ]
                }
                """;
        run(input,ModelConstant.SLICE_EVALUATOR_PROMPT);
    }

//    @Test
    public void coreModelTest(){
        String input = """
                {
                  "text": "在",
                  "datetime": "2024-03-22T09:00",
                  "character": "你是一个智能助手，专注于科技领域",
                  "memory_slices": [
                    {
                      "chatMessages": [
                        {"role": "user", "content": "量子计算近期的进展怎么样？"},
                        {"role": "assistant", "content": "量子计算在硬件和算法上都取得了突破，IBM发布了433量子位处理器，Google也在量子优越性上取得了进展。"}
                      ],
                      "date": "2024-03-20",
                      "summary": "量子计算最新突破：IBM发布433量子位处理器，Google在量子优越性上取得进展。"
                    }
                  ],
                  "static_memory": "用户对量子计算技术非常感兴趣。",
                  "dialog_map": {
                    "2024-03-20T10:30": "与用户讨论了量子计算的最新进展"
                  },
                  "user_dialog_map": {
                    "2024-03-20T10:30": "与用户讨论了量子计算的最新进展"
                  }
                }
                
                """;
        run(input,ModelConstant.CORE_MODEL_PROMPT + "\r\n" + MemorySelector.modulePrompt);
    }

//    @Test
    public void map2jsonTest(){
        HashMap<LocalDate,String> map = new HashMap<>();
        map.put(LocalDate.now(),"hello");
        map.put(LocalDate.now().plusDays(1),"world");
        System.out.println(JSONUtil.toJsonPrettyStr(map));
    }

    private void run(String input, String prompt) {
        ChatClient client = new ChatClient("https://open.bigmodel.cn/api/paas/v4/chat/completions", "3db444552530b7742b0c53425fb93dcc.LcVwYjByht9AC3N9", "glm-4-flash-250414");
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(ChatConstant.Character.SYSTEM, prompt));

        messages.add(new Message(ChatConstant.Character.USER, input));
        System.out.println(client.runChat(messages).getMessage());
    }
}
