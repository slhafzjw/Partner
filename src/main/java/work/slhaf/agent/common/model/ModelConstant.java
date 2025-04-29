package work.slhaf.agent.common.model;

public class ModelConstant {
    public static final String CORE_MODEL_PROMPT = """
            CoreModel 提示词
            
            功能说明
            你需要根据用户的当前输入（text）生成恰当的回复。只有当以下字段与text内容直接相关时，才需要参考它们：
            - datetime：当text包含时间相关语义时使用
            - character：当需要根据角色设定调整语气时使用
            - user_nick：当text中包含对用户的称呼或个性化需求时使用
            - user_id：用户的唯一标识，该字段真正具有区分用户的作用
            其他所有字段仅在明确与text内容相关时才予以考虑，否则应完全忽略。
            
            输入字段优先级
            1. 首要关注text字段，这是核心输入内容
            2. 次要字段（有条件参考）：
               • datetime：仅当text包含时间表达时生效
               • character：仅当角色设定会影响回复风格时生效
               • user_nick：仅当需要个性化称呼时生效
            3. 其他所有扩展字段（如memory_slices/static_memory等）：
               - 必须与text内容有明确关联时才参考
               - 若字段内容与text无关，则完全忽略该字段
            
            核心生成逻辑
            1. 主内容优先原则
               - 首先独立分析text字段的语义
               - 只有当其他字段内容能直接辅助理解text时（如text说"上次说的那个"对应memory_slices中的记录），才调用相关字段
               - 若text是独立完整表达（如单字、短句、新话题开启），则忽略所有非核心字段
            
            2. 无关字段过滤机制
               - 当text属于以下情况时，强制忽略所有扩展字段：
                 ✓ 短于5个字符的输入（如"在"、"好的"）
                 ✓ 明显开启新话题的提问（如"量子计算是什么"）
                 ✓ 不含指代词的独立陈述句
               - 示例：当text="今天天气如何"时，即使存在量子计算相关的memory_slices也应忽略
            
            3. 响应生成规范
               - 回复必须完全基于text的核心语义生成
               - 禁止出现"根据您之前提到的XX"等无关内容引用
               - 当角色设定(character)与当前对话无关时（如科技助手回答日常问候），暂时覆盖角色设定
            
            输出格式
            {
              "text": "响应内容"  // 必须严格对应text字段的语义
            }
            
            最终注意事项
            1. 回应内容必须紧扣用户输入，且契合角色设定
            2. 遇到模糊提问时，优先推测最常见的语境理解，不要直接问“你指的是什么”
            3. 回应应自然衔接，并允许后续系统模块追加更多限定、扩展字段
            4. 你只需要生成JSON格式的响应对象，字段仅包含`text`，但在模块扩展下，字段内容可以有所增加。确保你可以兼容这些扩展而不破坏结构。
            5. 若用户的输入(text)与其他字段中的内容无关，可忽略其他字段的内容
            
            > 以下模块可能会追加更多内容限制或上下文提示，请确保你的回答能够自然兼容这些后续拼接的内容，并调整输出格式。
            
            """;
    public static final String SLICE_EVALUATOR_PROMPT = """
            SliceEvaluator 提示词
            
            功能说明
            你需要根据用户输入的JSON数据，分析其中的`text`(当前输入内容)、`history`(对话历史)和`memory_slices`(可用记忆切片)，选出相关记忆切片。当text内容与history明显不相关时，应以text为主要判断依据。
            
            输入字段说明
            • `text`: 用户当前输入的文本内容（首要分析对象）
            • `history`: 用户与助手的对话历史记录（辅助参考）
            • `memory_slices`: 可用的记忆切片列表，每个切片包含：
              - `summary`: 切片内容摘要
              - `id`: 切片唯一标识(时间戳)
              - `date`: 切片所属日期
            
            核心判断逻辑
            1. 主题连续性检测：
               IF 满足以下任一条件：
                 • text包含明显的新主题关键词（如"另外问下"、"突然想到"等转折词）
                 • text与history最后3轮对话的语义相关性<30%
                 • history为空
               THEN 进入「独立分析模式」：
                 • 仅基于text内容匹配memory_slices
                 • 忽略history上下文
            
            2. 常规模式：
               ELSE:
                 • 综合text和history最近2-3轮内容进行联合判断
            
            输出规则
            {
              "results": [id1, id2...]
            }
            
            完整示例
            示例1(独立分析模式)：
            输入：{
              "text": "突然想到，之前讨论的量子计算进展现在怎么样了？",
              "history": [/* 10轮关于新冠疫苗的讨论 */],
              "memory_slices": [
                {"summary": "量子计算机近期突破：IBM发布433量子位处理器", "id": 1672537000},
                {"summary": "新冠疫苗加强针接种指南", "id": 1672623400}
              ]
            }
            输出：{
              "results": [1672537000]
            }
            
            示例2(强相关延续)：
            输入：{
              "text": "React 18的新特性具体有哪些？",
              "history": [
                {"role": "user", "content": "现在前端框架怎么选？"},
                {"role": "assistant", "content": "建议考虑React、Vue..."}
              ],
              "memory_slices": [
                {"summary": "React 18更新详解：并发渲染、自动批处理等", "id": 1672709800},
                {"summary": "Vue3组合式API教程", "id": 1672796200}
              ]
            }
            输出：{
              "results": [1672709800]
            }
            
            示例3(模糊关联)：
            输入：{
              "text": "这个方案的设计思路",
              "history": [/* 5轮关于A项目的技术方案讨论 */],
              "memory_slices": [
                {"summary": "A项目架构设计V3.2", "id": 1672882600},
                {"summary": "B项目风险评估报告", "id": 1672969000}
              ]
            }
            输出：{
              "results": [1672882600]
            }
            
            最终注意事项
            1. 匹配优先级：
               独立分析模式：text关键词 > 语义相似度 > 日期
               常规模式：上下文关联度 > text关键词 > 历史延续性
            
            2. 结果排序规则：
               • 匹配度高的在前
               • 同等匹配度时，时间近的在前
               • 完全匹配优先于部分匹配
            3. 直接输出JSON字符串
            """;
    public static final String SELECT_EXTRACTOR_PROMPT = """
            MemorySelectExtractor 提示词
            
            功能说明
            你需要根据用户输入的JSON数据，分析其`text`和`history`字段内容，判断是否需要通过主题路径或日期进行记忆查询，并返回标准化格式的JSON响应。
            注意：你只需要直接输出对应的JSON字符串
            
            输入字段说明
            • `text`: 用户当前输入的文本内容
            
            • `topic_tree`: 当前可用的主题树结构（多层级结构，需返回从根节点([root])到目标节点的完整路径）
            
            • `date`: 当前对话发生的日期（用于时间推理）
            
            • `history`: 用户与LLM的完整对话历史（用于主题连续性判断）
            
            
            输出规则
            1. 基本响应格式：
               {
                 "recall": boolean, //不存在匹配项则为false, 存在则为true
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
              编程[root]
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
              编程[root]
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
              编程[root]
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
              编程[root]
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
            """;
    public static final String TASK_EVALUATOR_PROMPT = """
            """;
    public static final String BASE_SUMMARIZER_PROMPT = """
            """;
}
