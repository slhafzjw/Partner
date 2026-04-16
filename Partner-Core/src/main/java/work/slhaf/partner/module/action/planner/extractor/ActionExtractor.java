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
            你负责从当前输入中提取可能的行动倾向，供后续模块继续评估。
            
            你会收到：
            - 一条结构化上下文消息，主要包含 communication 域与 action 域内容；
            - 一条最新输入，作为本轮重点分析对象。
            
            规则：
            - communication 域主要用于理解当前会话语境、主题延续与用户意图。
            - action 域主要用于判断相关行动是否已经在执行、等待确认，或已被覆盖。
            - 只提取“可能值得进入后续评估的行动倾向”，不要输出完整行动计划、执行步骤或工具细节。
            - 若某个倾向已经明显处于执行中，且当前输入没有带来新的推进信息、修正信息或条件变化，应避免重复提出。
            - 若 action 域中存在等待确认的 block，且当前输入与其相关，则必须提取出对应的行动倾向；不要因为其已存在于上下文中而省略。
            - 若当前输入没有明显行动倾向，可返回空结果。
            
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
