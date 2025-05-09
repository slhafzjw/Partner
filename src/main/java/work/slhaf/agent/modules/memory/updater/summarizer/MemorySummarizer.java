package work.slhaf.agent.modules.memory.updater.summarizer;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.constant.ChatConstant;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;
import work.slhaf.agent.core.interaction.InteractionThreadPoolExecutor;
import work.slhaf.agent.modules.memory.updater.summarizer.data.SummarizeInput;
import work.slhaf.agent.modules.memory.updater.summarizer.data.SummarizeResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static work.slhaf.agent.common.util.ExtractUtil.extractJson;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class MemorySummarizer extends Model {

    private static MemorySummarizer memorySummarizer;
    public static final String MODEL_KEY = "memory_summarizer";
    private static final List<String> prompts = List.of(
            Constant.SINGLE_SUMMARIZE_PROMPT,
            Constant.MULTI_SUMMARIZE_PROMPT,
            Constant.TOTAL_SUMMARIZE_PROMPT
    );

    private InteractionThreadPoolExecutor executor;

    public static MemorySummarizer getInstance() throws IOException, ClassNotFoundException {
        if (memorySummarizer == null) {
            memorySummarizer = new MemorySummarizer();
            memorySummarizer.setExecutor(InteractionThreadPoolExecutor.getInstance());
            setModel(Config.getConfig(), memorySummarizer, MODEL_KEY, ModelConstant.BASE_SUMMARIZER_PROMPT);
        }
        return memorySummarizer;
    }

    public SummarizeResult execute(SummarizeInput input) throws InterruptedException {
        //进行长文本批量摘要
        singleMessageSummarize(input.getChatMessages());
        //进行整体摘要并返回结果
        return multiMessageSummarize(input);
    }

    private SummarizeResult multiMessageSummarize(SummarizeInput input) {
        String messageStr = JSONUtil.toJsonPrettyStr(input);
        return multiSummarizeExecute(prompts.get(1), messageStr);
    }

    private SummarizeResult multiSummarizeExecute(String prompt, String messageStr) {
        ChatResponse response = chatClient.runChat(List.of(new Message(ChatConstant.Character.SYSTEM, prompt),
                new Message(ChatConstant.Character.USER, messageStr)));
        return JSONObject.parseObject(extractJson(response.getMessage()), SummarizeResult.class);
    }

    private void singleMessageSummarize(List<Message> chatMessages) {
        List<Callable<Void>> tasks = new ArrayList<>();
        for (Message chatMessage : chatMessages) {
            if (chatMessage.getRole().equals(ChatConstant.Character.ASSISTANT)) {
                String content = chatMessage.getContent();
                if (chatMessage.getContent().length() > 500) {
                    tasks.add(() -> {
                        chatMessage.setContent(singleSummarizeExecute(prompts.getFirst(), JSONObject.of("content", content).toString()));
                        return null;
                    });
                }
            }
        }
        executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
    }

    private @NonNull String singleSummarizeExecute(String prompt, String content) {
        try {
            ChatResponse response = chatClient.runChat(List.of(new Message(ChatConstant.Character.SYSTEM, prompt),
                    new Message(ChatConstant.Character.USER, content)));
            return JSONObject.parseObject(extractJson(response.getMessage())).getString("content");
        } catch (Exception e) {
            log.error(e.getLocalizedMessage());
            return content;
        }
    }


    public String executeTotalSummary(HashMap<String, String> singleMemorySummary) {
        ChatResponse response = chatClient.runChat(List.of(new Message(ChatConstant.Character.SYSTEM, prompts.get(2)),
                new Message(ChatConstant.Character.USER, JSONUtil.toJsonPrettyStr(singleMemorySummary))));
        return JSONObject.parseObject(extractJson(response.getMessage())).getString("value");
    }

    private static class Constant {
        public static final String SINGLE_SUMMARIZE_PROMPT = """
                SINGLE_SUMMARIZER 提示词
                功能说明
                你需要根据用户输入的JSON数据中的`content`字段内容，生成精简且保留关键细节的摘要，严格控制输出在500字以内。
                
                输入字段说明
                • `content`: 需要被摘要的原始文本内容（可能包含复杂信息或多段落结构）
                
                输出规则
                1. 基本响应格式：
                   {
                     "content": string // 摘要后的文本内容
                   }
                2. 摘要质量要求：
                   • 保留所有关键事实和数据
                   • 维持原始信息的因果关系
                   • 优先保留具体名词和数字信息
                   • 删除冗余修饰词和重复表达
                3. 长度控制：
                   • 硬性限制：绝对不超过500字符（按中文计算）
                   • 理想长度：200-450字符区间
                4. 特殊处理：
                   • 当检测到列表/条目信息时：改用分号连接
                   • 当存在直接引语时：保留核心引述但可简化引导句
                
                处理流程
                1. 首次扫描：识别文本中的关键要素（5W1H）
                2. 二次分析：标注需要保留的具体数据/专有名词
                3. 结构优化：
                   a. 合并同类段落
                   b. 转换长句为短句
                   c. 用更简洁的表达替换复杂句式
                4. 最终校验：检查是否丢失关键信息
                
                完整示例
                示例1（常规长文本）：
                输入：{
                  "content": "在2023年第四季度，XX公司实现了显著增长。财报显示总收入达到4.56亿元，同比增长32%。其中主要增长来自智能手机业务板块，该板块贡献了3.12亿元收入，同比增长达45%。同时智能家居业务收入1.44亿元，同比增长12%。公司CEO在财报电话会议中强调，增长主要得益于东南亚市场的成功拓展..."
                }
                输出：{
                  "content": "XX公司2023年Q4总收入4.56亿元（同比+32%），智能手机业务贡献3.12亿元（+45%），智能家居1.44亿元（+12%），增长主要来自东南亚市场拓展。"
                }
                
                示例2（多段落文本）：
                输入：{
                  "content": "本次项目改造涉及三个主要方面。首先，硬件升级包括：1) 更换全部服务器设备；2) 安装新的网络交换机；3) 部署智能安防系统。其次，软件系统将迁移至新平台，需完成数据迁移和接口适配。最后，人员培训计划分三阶段实施..."
                }
                输出：{
                  "content": "项目改造含硬件升级（更换服务器、新交换机、智能安防）、软件系统迁移（含数据迁移和接口适配）及分三阶段的人员培训。"
                }
                
                示例3（技术文档）：
                输入：{
                  "content": "该算法采用改进的卷积神经网络架构，包含3个主要模块：特征提取模块（由5个卷积层组成）、注意力机制模块（含通道和空间注意力）、以及分类模块（使用2个全连接层）。在ImageNet数据集上达到92.3%的准确率..."
                }
                输出：{
                  "content": "算法使用改进CNN架构，含特征提取（5卷积层）、注意力机制（通道+空间）和分类模块（2全连接层），在ImageNet上准确率92.3%。"
                }
                """;

        public static final String MULTI_SUMMARIZE_PROMPT = """
                DialogueTopicMapper 提示词
                功能说明
                分析对话内容并生成最深为7层的多层次主题路径，支持智能扩展主题树结构，根据用户意图动态调整路径生成策略。
                
                在保证符合以下要求的同时尽快输出
                
                输入字段说明
                • topicTree: 现有主题树结构（多根节点）
                • chatMessages: 完整对话记录（需分析双方发言）
                
                输出规则
                0. **只需要输出所需的JSON文本**
                1. 核心结构（保持原格式）：
                {
                  "summary": "",          // 精简摘要（100-150字）
                  "topicPath": "",        // 主路径（领域纯净的完整抽象链）
                  "relatedTopicPath": [], // 相关路径（允许跨领域）
                  "isPrivate": false
                }
                
                2. 主题路径生成细则：
                • 抽象链构建流程：
                  a. 以`user`的意图为主要锚点，锁定最低节点
                  b. 逐层抽象（地标→城市→国家→大洲）,需保证抽象链的纯净，确保不会跨越领域
                  c. 修剪抽象链，使其保持在[3, 7]层之内，同时每层的抽象节点考虑扩展性及可复用性
                  d. 形成最终路径（格式：领域→大类→子类→实例）
                
                • 意图影响规则：
                  用户意图类型      | 主路径特征                | 相关路径特征
                  ----------------|-------------------------|-------------------
                  知识咨询         | 聚焦专业领域链             | 补充相关学科
                  经验分享         | 生活场景链                | 关联文化/社会
                  事件讨论         | 时空维度链                | 链接相关事件
                
                3. 动态扩展规范：
                • 新根节点创建条件：
                  - 当抽象层级超过现有树结构时（如现有最高为"国家"，需创建"大洲"）
                  - 检测到全新领域维度时（如原树无"天文"相关节点）
                
                主题树格式示例
                （使用自然换行，无需转义符）
                地理[root]
                └── 欧洲
                     ├── 法国
                     └── 德国
                生活[root]
                └── 旅行
                     ├── 自由行
                     └── 跟团游
                
                处理流程
                1. 意图分析阶段：
                   a. 判断对话类型（咨询/分享/讨论）
                   b. 标记关键实体和动作
                2. 路径构建阶段：
                   a. 自下而上构建抽象链（实例→抽象概念）
                   b. 验证层级逻辑（子类必须属于父类范畴）
                   c. 生成最终路径（格式示例：生活->旅行->自由行->欧洲游）
                3. 扩展校验阶段：
                   a. 新增节点必须通过逻辑验证
                   b. 技术术语需符合行业标准
                
                完整示例
                示例1（日常分享）：
                输入：{
                  "topicTree": "
                生活[root]
                └── 旅行",
                  "chatMessages": [
                    {"role": "user", "content": "刚完成欧洲自由行，在巴黎铁塔拍到绝美夜景"},
                    {"role": "assistant", "content": "推荐使用Lightroom处理夜景RAW格式"}
                  ]
                }
                输出：{
                  "summary": "用户分享欧洲自由行经历并讨论夜景照片处理",
                  "topicPath": "生活->旅行->自由行->欧洲->法国->巴黎铁塔",
                  "relatedTopicPath": [
                    "艺术->摄影->夜景拍摄",
                    "科技->软件->图像处理->Lightroom"
                  ],
                  "isPrivate": false
                }
                
                示例2（专业咨询）：
                输入：{
                  "topicTree": "
                计算机[root]
                └── 编程",
                  "chatMessages": [
                    {"role": "user", "content": "SpringBoot项目如何实现JWT鉴权"},
                    {"role": "assistant", "content": "需集成spring-security-jwt依赖..."}
                  ]
                }
                输出：{
                  "summary": "讨论SpringBoot项目集成JWT鉴权的技术方案",
                  "topicPath": "计算机->软件开发->Java->SpringBoot->安全->JWT",
                  "relatedTopicPath": [
                    "计算机->网络安全->认证协议",
                    "数学->加密算法->非对称加密"
                  ],
                  "isPrivate": false
                }
                
                示例3（事件讨论）：
                输入：{
                  "topicTree": "
                社会[root]
                ├── 教育
                └── 科技",
                  "chatMessages": [
                    {"role": "user", "content": "听说某大学研发出脑机接口新成果"},
                    {"role": "assistant", "content": "该技术涉及神经科学和AI的跨学科研究"}
                  ]
                }
                输出：{
                  "summary": "讨论某大学在脑机接口领域的跨学科研究成果",
                  "topicPath": "社会->科技->人工智能->脑机接口",
                  "relatedTopicPath": [
                    "科学->生物学->神经科学",
                    "教育->高等教育->科研创新"
                  ],
                  "isPrivate": false
                }
                
                示例4（隐私事件）：
                输入：{
                  "topicTree": "
                法律[root]
                └── 隐私",
                  "chatMessages": [
                    {"role": "user", "content": "这个合同条款请仅限我们之间知晓"},
                    {"role": "assistant", "content": "已启用加密存储，不会外泄"}
                  ]
                }
                输出：{
                  "summary": "用户要求保密合同条款内容",
                  "topicPath": "法律->合同法->保密条款",
                  "relatedTopicPath": ["信息技术->数据安全->加密存储"],
                  "isPrivate": true
                }
                
                """;

        public static final String TOTAL_SUMMARIZE_PROMPT = """
                TOTAL_SUMMARIZER 提示词
                功能说明
                你需要根据输入的多个独立用户对话摘要，生成一份综合性的总结报告。每个用户的对话内容彼此无关联，需保持原始信息的同时进行概括性整合，最终输出标准化JSON格式的响应。
                
                输入字段说明
                • 输入数据为JSON对象：
                  - key: 用户uuid（需在输出中保留）
                  - value: 该用户的对话摘要文本（需要处理的内容）
                
                输出规则
                1. 基本响应格式：
                   {
                     "content": string // 综合摘要文本
                   }
                2. 内容要求：
                   • 严格控制在800字以内
                   • 保持客观中立，不添加解释性内容
                   • 使用分号分隔不同用户的摘要内容
                   • 保留原始对话的关键事实信息
                   • 对重复信息进行合并处理
                3. 格式要求：
                   • 每个用户摘要以"用户[uuid]："开头
                   • 不同用户摘要间用分号分隔
                   • 末尾不添加总结性陈述
                
                处理流程
                1. 解析输入JSON的所有键值对
                2. 对每个摘要执行：
                   a. 提取关键事实信息
                   b. 删除问候语等非实质性内容
                   c. 简化重复表达
                3. 合并处理：
                   a. 识别不同摘要中的相同信息点
                   b. 合并相同信息点的不同表述
                4. 生成最终摘要：
                   a. 按原始输入顺序排列用户摘要
                   b. 确保总字数≤800
                   c. 验证信息完整性
                
                完整示例
                示例1（基础情况）：
                输入：{
                  "aaa-111": "需要购买笔记本电脑，预算5000左右，主要用于办公",
                  "bbb-222": "想买游戏本，预算8000-10000，要能运行3A大作",
                  "ccc-333": "咨询轻薄本推荐，经常出差使用"
                }
                输出：{
                  "content": "
                用户[aaa-111]：需要5000元左右的办公笔记本；
                用户[bbb-222]：寻求8000-10000元的游戏本，要求能运行3A大作；
                用户[ccc-333]：咨询适合出差使用的轻薄本"
                }
                
                示例2（信息合并）：
                输入：{
                  "ddd-444": "想了解Python入门课程，零基础",
                  "eee-555": "询问Java和Python哪个更适合新手",
                  "fff-666": "零基础，想学Python数据分析"
                }
                输出：{
                  "content": "
                用户[ddd-444]：零基础想了解Python入门课程；
                用户[eee-555]：询问Java和Python对新手的适用性；
                用户[fff-666]：零基础想学习Python数据分析"
                }
                
                示例3（长文本精简）：
                输入：{
                  "ggg-777": "您好！我最近在准备考研，想咨询下时间规划。具体是想了解每天应该分配多少时间给英语复习，我现在英语水平大概是四级刚过的程度...（后续200字详细描述）",
                  "hhh-888": "考研政治怎么准备？需要报班吗？"
                }
                输出：{
                  "content": "
                用户[ggg-777]：咨询考研英语复习时间规划，当前英语水平为四级；
                用户[hhh-888]：询问考研政治备考方法及是否需要报班"
                }
                
                特殊处理
                1. 当总字数超出限制时：
                   • 尽量保留所有出现的用户摘要
                2. 当输入为空时：
                   {
                     "content": ""
                   }
                3. 当用户uuid包含特殊字符时：
                   • 保持原始uuid格式不做修改
                   • 示例：用户[xxx-ddssss-xx]：内容摘要
                """;
    }
}
