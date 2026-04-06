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
import work.slhaf.partner.module.TaskBlock;
import work.slhaf.partner.module.action.executor.entity.CorrectorInput;
import work.slhaf.partner.module.action.executor.entity.CorrectorResult;

import java.util.List;

/**
 * 负责在单组行动执行后，根据行动意图与结果检查后续行动是否符合目的，必要时直接调整行动链，或发起自对话请求进行干预
 */
public class ActionCorrector extends AbstractAgentModule.Sub<CorrectorInput, CorrectorResult> implements ActivateModel {

    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    public CorrectorResult execute(CorrectorInput input) {
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
                ContextBlock.VisibleDomain.ACTION,
                ContextBlock.VisibleDomain.COGNITION,
                ContextBlock.VisibleDomain.MEMORY
        )).encodeToMessage();
    }

    @NotNull
    @Override
    public String modelKey() {
        return "action_corrector";
    }
}
