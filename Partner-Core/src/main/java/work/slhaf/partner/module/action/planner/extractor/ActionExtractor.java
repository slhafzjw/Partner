package work.slhaf.partner.module.action.planner.extractor;

import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;
import work.slhaf.partner.framework.agent.exception.ModuleExecutionException;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.action.planner.extractor.entity.ExtractorResult;

import java.util.List;

public class ActionExtractor extends AbstractAgentModule.Sub<String, Result<ExtractorResult>> implements ActivateModel {

    private static final String MODULE_PROMPT = """
            你负责从当前输入中提取“可能值得进入后续行动评估”的行动倾向。
            
            你会收到：
            - 一条结构化上下文消息，主要包含 communication 域、perceive 域与 action 域内容；
            - 一条最新输入，作为本轮重点分析对象。
            
            你的任务：
            - 基于当前输入与上下文，提取少量、明确、值得继续进入行动评估的行动倾向；
            - 这里只提取“可能需要通过行动推进”的倾向，而不是所有可回应的意图；
            - 若当前输入更适合直接交流回应，而不需要额外行动推进，则返回空结果。
            
            基本原则：
            - communication 域主要用于理解当前会话语境、话题延续、指代对象与用户真实意图。
            - perceive 域可用于理解当前系统状态、环境信息、时间信息等；若当前输入仅是在询问这些已可直接回答的信息，不应因此提取行动倾向。
            - action 域主要用于判断相关行动是否已经在执行、等待确认、或已被覆盖。
            - 只提取“可能值得进入后续评估的行动倾向”，不要输出完整行动计划、执行步骤、工具细节或命令内容。
            - 若某个倾向已经明显处于执行中，且当前输入没有带来新的推进信息、修正信息、确认信息或条件变化，应避免重复提出。
            - 若 action 域中存在等待确认的 block，且当前输入与其相关，则必须提取出对应的行动倾向；不要因为其已存在于上下文中而省略。
            
            复合任务合并规则：
            - 若当前输入包含“先...再...最后...”“并且/然后/同时”等多个动作描述，但这些动作共同服务于同一个最终目标，应提取为一个整体行动倾向，而不是拆成多个并列倾向。
            - 中间的信息获取、检查、读取、查询步骤如果只是为了支撑最终判断、汇报、生成结果，不应作为独立 tendency 输出。
            - 最终的“总结、判断、汇报、说明结果”如果依赖前置行动结果，应并入同一个整体 tendency，而不是作为单独的 communication 类 tendency 输出。
            - 只有当多个动作目标彼此独立、可以分别完成且不共享同一个最终目的时，才输出多个 tendency。
            
            什么情况下应提取行动倾向：
            - 用户明确要求系统执行某个动作、调用某项能力、访问某类外部信息、操作某个对象或推进某个待办；
            - 用户要求修改、继续、取消、确认或补充某个已有待处理行动；
            - 用户明确要求系统代其完成某件事，而不是仅作解释、说明或回答；
            - 若不进入行动链，则该请求无法被真正推进或完成。
            
            什么情况下不应提取行动倾向：
            - 当前输入本质上是一个可直接回答的提问；
            - 当前输入只是要求解释、分析、总结、说明、翻译、评价、闲聊或感叹；
            - 当前输入只是询问当前上下文、系统状态、已知信息、记忆内容或当前可见内容，而这些内容可直接通过交流给出；
            - 当前输入只是一般性的信息查询，但没有明确要求系统通过外部行动补充信息；
            - 当前输入只是对已有回复的轻量追问、澄清或自然延续，仍然更适合直接回应；
            - 当前输入语义模糊，仅凭“可能有东西可做”不足以进入行动评估；
            - 当前输入虽然提到某个对象或任务，但并未形成明确的行动推进意图。
            
            重要限制：
            - 不要把“可以通过行动获得更多信息”当成提取理由。
            - 不要把“询问信息”自动等同于“要求去执行查询动作”。
            - 不要为了提高召回而泛化提取。
            - 如果更适合 communication 直接回答，就返回空结果。
            - 倾向应简洁明确，表达“要推进什么”，不要写成自然语言执行方案。
            
            输出要求：
            - 严格按照 ExtractorResult 对应结构输出。
            - 不要输出结构之外的解释或额外文本。
            """;

    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    protected @NotNull Result<ExtractorResult> doExecute(String input) {
        List<Message> messages = List.of(
                cognitionCapability.contextWorkspace().resolve(List.of(
                        ContextBlock.FocusedDomain.COMMUNICATION,
                        ContextBlock.FocusedDomain.PERCEIVE,
                        ContextBlock.FocusedDomain.ACTION
                )).encodeToMessage(),
                new Message(Message.Character.USER, input)
        );
        try {
            return Result.success(formattedChat(messages, ExtractorResult.class).getOrThrow());
        } catch (AgentRuntimeException e) {
            return Result.failure(new ModuleExecutionException(
                    "collecting action tendencies failed",
                    this.getClass(),
                    getModuleName()
            ));
        }
    }

    @Override
    @NotNull
    public List<Message> modulePrompt() {
        return List.of(new Message(Message.Character.SYSTEM, MODULE_PROMPT));
    }

    @NotNull
    @Override
    public String modelKey() {
        return "action_extractor";
    }
}
