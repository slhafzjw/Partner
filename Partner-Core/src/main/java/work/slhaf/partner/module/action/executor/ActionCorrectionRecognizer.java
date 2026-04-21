package work.slhaf.partner.module.action.executor;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.TaskBlock;
import work.slhaf.partner.module.action.executor.entity.CorrectorInput;
import work.slhaf.partner.module.action.executor.entity.RecognizerResult;

import java.util.List;

/**
 * 负责在行动链执行过程中判断当前进度是否异常，是否需要引入 corrector 介入。
 */
public class ActionCorrectionRecognizer extends AbstractAgentModule.Sub<CorrectorInput, Result<RecognizerResult>> implements ActivateModel {

    private static final String MODULE_PROMPT = """
            你负责在行动链执行过程中识别当前执行是否出现异常，并判断是否需要引入 corrector 介入。
            
            你会收到：
            - 一条结构化上下文消息，其中包含近期交流轨迹与当前活跃记忆切片；
            - 一条任务消息，其中包含：
              - executable_action_info：当前正在执行的行动信息，包括 executing_action_id、original_tendency、evaluation_passed_reason、description 与 from_who；
              - current_action_chain_overview：当前行动链概览，按 stage_count 分组，包含各阶段已有 meta_action 的 action_key、description 与 status。
            
            你的任务：
            - 基于当前上下文、当前行动信息与当前行动链概览，判断这条行动链是否仍在合理推进；
            - 判断当前是否已经出现明显偏航、停滞、重复打转、条件失配、目标漂移，或与最新语境不再一致的情况；
            - 若需要引入 corrector，则返回 needCorrection=true，并给出简洁明确的 reason；
            - 若当前仍可继续推进，则返回 needCorrection=false。
            
            识别原则：
            - executable_action_info 用于说明当前链路最初为何成立、要解决什么问题、当前由谁发起；你的判断必须围绕这条行动本身，而不是被无关历史带偏。
            - executing_action_id 用于标识当前正在执行的行动；current_action_chain_overview 用于说明当前链路整体结构与已知阶段状态。
            - original_tendency 表示这条行动链最初要解决的问题；若当前链路明显偏离这一倾向，应优先视为异常信号。
            - evaluation_passed_reason 与 description 表示该行动最初为何被判定为可推进；若当前状态已经与这些前提不再一致，应考虑触发纠正。
            - current_action_chain_overview 是判断当前链路是否停滞、重复、缺步骤、顺序异常或整体失衡的主要依据。
            - communication 域用于判断最新交流语境是否已发生明显变化，导致当前行动继续推进不再合适。
            - memory 域只在与当前行动明显相关时作为辅助参考使用。
            
            应优先考虑需要纠正的情形：
            - 当前行动长期没有形成有效推进，只在重复相近步骤、相近动作或相近结论；
            - 当前执行显著偏离 original_tendency，开始围绕无关目标展开；
            - 当前行动所依赖的前提已被新的交流内容或上下文推翻；
            - 当前行动链反复遇到同类失败、阻塞或空转迹象，继续按原链推进意义不大；
            - 当前行动链已经明显需要改写策略、调整阶段顺序、补入新步骤或删除无效步骤，但现有链路无法自行收敛。
            
            不应轻易触发纠正的情形：
            - 只是正常的多步推进；
            - 存在短暂等待、一次性失败或合理重试，但整体方向仍正确；
            - 行动链整体仍与 original_tendency、evaluation_passed_reason 和当前语境保持一致；
            - 仅凭旧对话、低相关记忆或轻微波动，不足以判定当前行动异常。
            
            关于输出：
            - needCorrection=true 表示当前应引入 corrector 介入。
            - needCorrection=false 表示当前行动仍可继续按既有链路推进。
            - reason 用于简洁说明判断依据；若 needCorrection=false，也应给出简短理由，说明为何当前仍可继续推进。
            - 不要输出结构之外的解释、说明或额外文本。
            
            输出要求：
            - 严格按照 RecognizerResult 对应结构输出。
            """;

    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    protected @NotNull Result<RecognizerResult> doExecute(CorrectorInput input) {
        List<Message> messages = List.of(
                resolveContextMessage(),
                resolveTaskMessage(input)
        );
        return formattedChat(messages, RecognizerResult.class);
    }

    private Message resolveTaskMessage(CorrectorInput input) {
        return new TaskBlock() {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendChildElement(document, root, "executable_action_info", block -> {
                    appendTextElement(document, block, "executing_action_id", input.getActionId());
                    appendTextElement(document, block, "original_tendency", input.getTendency());
                    appendTextElement(document, block, "evaluation_passed_reason", input.getReason());
                    appendTextElement(document, block, "description", input.getDescription());
                    appendTextElement(document, block, "from_who", input.getSource());
                    return Unit.INSTANCE;
                });

                appendListElement(document, root, "current_action_chain_overview", "action_chain_stage", input.getActionChainOverview().entrySet(), (stageElement, stageData) -> {
                    stageElement.setAttribute("stage_count", String.valueOf(stageData.getKey()));
                    appendRepeatedElements(document, stageElement, "meta_action", stageData.getValue(), (metaActionElement, metaActionData) -> {
                        appendTextElement(document, metaActionElement, "action_key", metaActionData.getActionKey());
                        appendTextElement(document, metaActionElement, "description", metaActionData.getDescription());
                        appendTextElement(document, metaActionElement, "status", metaActionData.getStatus());
                        return Unit.INSTANCE;
                    });
                    return Unit.INSTANCE;
                });
            }
        }.encodeToMessage();
    }

    private Message resolveContextMessage() {
        return cognitionCapability.contextWorkspace().resolve(List.of(
                ContextBlock.FocusedDomain.COMMUNICATION,
                ContextBlock.FocusedDomain.PERCEIVE,
                ContextBlock.FocusedDomain.MEMORY
        )).encodeToMessage();
    }

    @Override
    @NotNull
    public List<Message> modulePrompt() {
        return List.of(new Message(Message.Character.SYSTEM, MODULE_PROMPT));
    }

    @NotNull
    @Override
    public String modelKey() {
        return "action_correction_recognizer";
    }
}
