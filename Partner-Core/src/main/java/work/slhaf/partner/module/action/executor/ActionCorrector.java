package work.slhaf.partner.module.action.executor;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.TaskBlock;
import work.slhaf.partner.module.action.executor.entity.CorrectorInput;
import work.slhaf.partner.module.action.executor.entity.CorrectorResult;

import java.util.List;

/**
 * 负责在单组行动执行后，根据行动意图与结果检查后续行动是否符合目的，必要时直接调整行动链，或发起自对话请求进行干预
 */
public class ActionCorrector extends AbstractAgentModule.Sub<CorrectorInput, Result<CorrectorResult>> implements ActivateModel {

    private static final String MODULE_PROMPT = """
            你负责在行动链执行过程中进行纠偏。你的任务不是重新评估是否要启动这条行动，而是在当前行动已经进入执行后，根据原始行动意图、当前链路结构与最新语境，判断是否需要调整后续行动链。
            
            你会收到：
            - 一条结构化上下文消息，其中包含近期交流轨迹与当前活跃记忆切片；
            - 一条任务消息，其中包含：
              - check_mode：当前纠偏模式，PROCESS_CHECK 表示过程纠偏，FINAL_CHECK 表示链路末尾目标未满足后的补救；
              - executable_action_info：当前正在执行的行动信息，包括 executing_action_id、original_tendency、evaluation_passed_reason、description 与 from_who；
              - current_action_chain_overview：当前行动链概览，按 stage_count 分组，包含各阶段已有 meta_action 的 action_key、description、status 与 result。
              - available_meta_action：当前系统真实可用的 MetaAction 候选，包含 meta_action_key 与 meta_action_description。
            
            你的任务：
            - 当 check_mode=PROCESS_CHECK 时，基于当前上下文、原始行动意图与当前行动链进展，判断后续行动是否仍然符合目的；
            - 当 check_mode=FINAL_CHECK 时，基于当前行动链的最终结果判断 original_tendency 尚未满足时应如何补救；
            - 若当前链路仍可继续推进或最终结果已足够满足目标，则不要随意干预；
            - 若当前链路已明显跑偏、缺少必要步骤、顺序不合理、存在冗余、已经不再适合继续，或需要引入新的动作单元，则输出干预方案；
            - FINAL_CHECK 下若结果显示目标未满足，应优先补入可继续执行的后续动作；如果无法补救，可以输出空 intervention 并在 correctionReason 中说明原因。
            - correctionReason 用于简洁说明为何需要这些纠偏。
            
            纠偏原则：
            - executable_action_info 用于说明当前链路最初为何成立、要解决什么问题、当前由谁发起；你的纠偏必须围绕这条行动本身，而不是转向新的无关目标。
            - executing_action_id 用于标识当前正在执行的行动；current_action_chain_overview 用于说明当前链路整体结构与阶段状态。
            - original_tendency 是这条行动链最初要解决的问题；后续行动的调整必须仍然围绕它。
            - evaluation_passed_reason 与 description 表示该行动最初为何能够成立；若当前链路已经与这些前提不一致，可据此进行纠偏。
            - current_action_chain_overview 是判断后续链路是否缺步骤、顺序失衡、重复冗余或整体方向错误的主要依据。
            - available_meta_action 是你进行 APPEND、INSERT 或 REBUILD 时可选择的动作全集；不要使用其中不存在的 action_key。
            - communication 域用于判断最新交流语境是否已经变化，导致当前链路需要调整。
            - memory 域只在与当前行动明显相关时作为辅助参考使用。
            
            何时应考虑干预：
            - 当前链路缺少继续推进所必需的动作；
            - 当前链路顺序明显不合理，继续执行会降低成功率或偏离目的；
            - 某些动作已经失效、重复、无意义，或不再适合当前状态；
            - 当前链路在某一阶段之后整体方向需要重建；
            - 当前行动已不应继续推进，应直接取消整条链路。
            
            何时不应轻易干预：
            - 当前只是正常的多步推进；
            - 某一步刚完成，尚不足以说明后续链路错误；
            - 没有足够依据证明当前行动链存在明显问题；
            - 仅凭旧对话、低相关记忆或轻微波动，不足以支持修改行动链。
            
            关于干预类型：
            - APPEND: 在指定 order 之后追加新的动作。
            - INSERT: 在指定 order 执行过程中即时插入并执行新的动作。
            - DELETE: 删除指定 order 上的指定动作。
            - CANCEL: 取消当前行动链后续执行。
            - REBUILD: 清空当前行动链与既有执行进度，并用新的规划内容整体重建行动链。
            
            关于 intervention 列表：
            - 系统会按照 metaInterventionList 的顺序逐条应用干预；前面的干预可能改变后续可理解的 order 位置，因此你在生成后续 intervention 时，必须考虑前面 intervention 已经生效后的链路形态。
            - 不要把一组本应整体表达的修改拆成互相冲突、顺序不自洽的 intervention。
            - actions 中只能填写当前系统中真实存在、可用的 action_key，不要编造不存在的动作。
            - order 必须对应当前行动链中的合理阶段位置。
            
            关于 REBUILD：
            - REBUILD 表示放弃当前既有链路，并重新给出新的整体规划。
            - 一旦本次纠偏结果中使用了 REBUILD，则 metaInterventionList 中所有 intervention 都必须是 REBUILD；不要将 REBUILD 与 APPEND、INSERT、DELETE、CANCEL 混用。
            - 使用 REBUILD 时，应把新的链路规划完整表达为一组按 order 分布的 REBUILD intervention，而不是只局部补几步。
            
            其他约束：
            - 若无需干预，可返回空的 metaInterventionList，并给出简短的 correctionReason 说明当前为何可继续推进。
            - 不要输出结构之外的解释、说明或额外文本。
            
            输出要求：
            - 严格按照 CorrectorResult 对应结构输出。
            - metaInterventionList 中每一项都必须是明确、可执行、且与其他 intervention 顺序一致的干预单元。
            """;

    @InjectCapability
    private CognitionCapability cognitionCapability;
    @InjectCapability
    private ActionCapability actionCapability;

    @Override
    protected @NotNull Result<CorrectorResult> doExecute(CorrectorInput input) {
        List<Message> messages = List.of(
                resolveContextMessage(),
                resolveTaskMessage(input)
        );
        return formattedChat(messages, CorrectorResult.class);
    }

    private Message resolveTaskMessage(CorrectorInput input) {
        return new TaskBlock() {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendTextElement(document, root, "check_mode", input.getCheckMode());
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
                        appendTextElement(document, metaActionElement, "result", metaActionData.getResult());
                        return Unit.INSTANCE;
                    });
                    return Unit.INSTANCE;
                });
                appendRepeatedElements(
                        document,
                        root,
                        "available_meta_action",
                        actionCapability.listAvailableMetaActions().entrySet(),
                        (block, value) -> {
                            appendTextElement(document, block, "meta_action_key", value.getKey());
                            appendTextElement(document, block, "meta_action_description", value.getValue().getDescription());
                            return Unit.INSTANCE;
                        }
                );
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
        return "action_corrector";
    }
}
