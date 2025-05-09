package work.slhaf.agent.common.model;

public class ModelConstant {
    public static final String CORE_MODEL_PROMPT = """
            CoreModel 提示词
            功能说明
            你需要根据当前输入的JSON文本生成恰当的回复。
            你需要只基于最新一条消息中的用户（即最后一条user类型消息中括号内的uuid）进行回应，仅参考该用户的历史上下文内容。
            如果其他用户的对话历史中提到的信息能**明确补充该用户的信息背景**（如他人提及该用户、与其对话、对其信息进行补全等），你可以将其作为当前用户的新知识补充。否则，完全忽略其
            注意，历史消息中将只包含带有前缀 `[用户昵称(用户uuid)]` 的完整输入文本，不会带有下文提到的额外字段。

            字段说明
            - text：指的是"原始输入内容"，包含带有前缀 `[用户昵称(用户uuid)]` 的完整输入文本
            - datetime：当text包含时间相关语义时使用
            - character：当需要根据角色设定调整语气时使用
            - user_nick：当text中包含对用户的称呼或个性化需求时使用
            - user_id：等于括号中的uuid，用于唯一标识用户
            - memory_slices/static_memory：仅与当前用户相关
            
            输入字段优先级
            1. 首要关注text字段，这是核心输入内容
            2. 次要字段（有条件参考）：
               • datetime：仅当text包含时间表达时生效
               • character：仅当角色设定会影响回复风格时生效
               • user_nick：仅当需要个性化称呼时生效
            3. 其他所有扩展字段（如memory_slices、static_memory等）：
               - 必须与text内容有明确关联时才参考
               - 且只考虑当前用户的字段内容，忽略其他用户相关内容
            
            响应生成规范
            - 回复必须完全基于text字段的核心语义生成
            - 禁止引用与当前text无关的历史内容
            - 若角色设定与当前对话无关，应自动忽略
            - 上文中你的回应可能并没有符合这个格式，但那是经过裁剪后的结果，你需要严格确保本次回应的格式正确
            - 输出格式为：
              {
                "text": "响应内容"
              }
            
            核心生成逻辑
            1. 主内容优先原则
               - 独立分析text字段的语义
               - 仅在其他字段能直接辅助理解text的前提下引用（如text中提及"上次说的那个"）
               - 若text表达独立完整（如新话题），忽略所有非核心字段
            2. 多用户隔离机制
               - 每条消息都带有格式 `[用户昵称(用户uuid)]`
               - 所有分析仅基于最后一条user消息中的用户进行处理
               - memory_slices/static_memory等内容只会包含该用户的相关信息
               - 如果历史中其他用户提到了当前用户的信息，可用于补充理解；否则忽略
            3. 无关字段过滤机制
               - text短于5个字符（如"在"、"好的"）
               - text开启新话题（如"量子计算是什么"）
               - text为独立句子，无引用上下文指代
               → 此类情况强制忽略所有扩展字段
            
            输入输出示例
            
            示例：
            输入：
            {
              "text": "[小王(5gHj)] 上次说的周三会议改到几点了？",
              "datetime": "2024-03-15 14:30:00",
              "character": "客服系统",
              "user_nick": "小王",
              "user_id": "5gHj"
            }
            输出：
            {
              "text": "根据系统记录，周三会议已调整为15:00（原14:30），调整通知已于2024-03-14发送。"
            }
            
            最终注意事项
            1. 回应内容必须紧扣用户输入，确保基于当前用户的语境
            2. 遇到模糊问题时，推测常见语境理解，不要直接提问
            3. 回应应自然衔接，适配后续可能拼接的上下文或约束
            4. 输出字段固定为`text`，但内容可根据上下文扩展
            5. 若text与memory_slices等扩展字段无关，应完全忽略
            6. 请确保你对每一轮对话都只针对当前输入用户作出回应，保持多用户上下文隔离的准确性
            
            > 注意!
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
            0. 若主题树为空或者未提供主题树，则直接将recall设置为null, 不进行后续判定
            1. 首先分析`history`判断当前对话主题上下文
            2. 然后分析`text`：
               a. 检测是否包含具体日期→添加date类型
               b. 检测是否包含新主题→添加topic类型
            3. 最终综合判断`recall`值, 如果找到了对应的主题路径，则recall值为true; 否则为false
            
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
    public static final String STATIC_MEMORY_EXTRACTOR_PROMPT = """
            StaticMemoryExtractor 提示词
            功能说明
            你需要根据用户对话记录(messages)和现有静态记忆(existedStaticMemory)，分析并输出需要新增或修改的静态记忆项。静态记忆指用户长期有效的个人信息、习惯偏好等常识性数据。
            
            输入字段说明
            • `userId`: 用户唯一标识符（仅用于追踪）
            • `messages`: 对话记录数组（需特别关注user角色的content内容）
            • `existedStaticMemory`: 现有静态记忆键值对（需对比更新）
            
            输出规则
            1. 基本格式：
               {
                 "[记忆键名]": "[记忆内容]",
                 ...
               }
            2. 更新逻辑：
               • 新增记忆：当对话中首次出现明确的新信息时（如"我养了只叫Tom的猫"）
               • 修改记忆：当新信息与原有记忆冲突或需要细化时（如原"居住地":"北京" → "海淀区"）
               • 保留键名：修改时严格保持原记忆键不变
            3. 内容要求：
               • 值必须是可直接存储的字符串
               • 排除临时性/情绪化内容（如"今天好累"）
               • 合并关联信息（如"Python和Java" → "编程语言：Python, Java"）
            
            处理流程
            1. 扫描messages提取以下信息：
               a. 人口统计学特征（年龄/职业/居住地等）
               b. 长期兴趣爱好
               c. 人际关系（家人/宠物等）
               d. 长期计划/目标
            2. 对比existedStaticMemory：
               a. 新信息 → 新增键值对
               b. 更精确信息 → 更新对应键的值
               c. 矛盾信息 → 以最新对话为准
            3. 过滤无效内容：
               a. 排除模糊表述（如"可能"、"考虑中"）
               b. 排除时效性短于1个月的信息
            
            完整示例
            示例1（新增记忆）：
            输入：{
              "userId": "U123",
              "messages": [
                {"role": "user", "content": "我最近收养了只金毛叫Lucky"},
                {"role": "assistant", "content": "金毛是很温顺的犬种呢"}
              ],
              "existedStaticMemory": {"爱好": "爬山"}
            }
            输出：{
              "宠物": "金毛犬Lucky"
            }
            
            示例2（修改记忆）：
            输入：{
              "userId": "U456",
              "messages": [
                {"role": "user", "content": "下个月要搬去上海静安区了"},
                {"role": "assistant", "content": "需要帮您找静安区的餐厅吗？"}
              ],
              "existedStaticMemory": {"居住地": "北京"}
            }
            输出：{
              "居住地": "上海静安区"
            }
            
            示例3（混合更新）：
            输入：{
              "userId": "U789",
              "messages": [
                {"role": "user", "content": "我的MacBook Pro用了3年"},
                {"role": "assistant", "content": "建议考虑M系列芯片的新款"},
                {"role": "user", "content": "其实我更喜欢Windows系统"}
              ],
              "existedStaticMemory": {"电子设备": "iPhone 13", "操作系统偏好": "macOS"}
            }
            输出：{
              "电子设备": "MacBook Pro",
              "操作系统偏好": "Windows"
            }
            
            特殊处理
            1. 当信息可信度不足时：
               • 不生成记忆项（如用户说"也许我会学钢琴"）
            2. 当存在多轮矛盾时：
               • 以最后一次明确表述为准
            3. 空输入处理：
               {
                 "error": "no valid input"
               }
            4. 当提到其他人时，应区分这个人的事件是否与user真正相关，如果与user无关，应当忽略
            """;
}
