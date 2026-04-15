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
import work.slhaf.partner.module.action.executor.entity.CorrectionRecognizerInput;
import work.slhaf.partner.module.action.executor.entity.CorrectionRecognizerResult;

import java.util.List;

/**
 * 负责在行动链执行过程中判断当前进度是否异常，是否需要引入 corrector 介入。
 */
public class ActionCorrectionRecognizer extends AbstractAgentModule.Sub<CorrectionRecognizerInput, Result<CorrectionRecognizerResult>> implements ActivateModel {

    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    protected @NotNull Result<CorrectionRecognizerResult> doExecute(CorrectionRecognizerInput input) {
        List<Message> messages = List.of(
                resolveContextMessage(),
                resolveTaskMessage(input)
        );
        return formattedChat(messages, CorrectionRecognizerResult.class);
    }

    private Message resolveTaskMessage(CorrectionRecognizerInput input) {
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

            }
        }.encodeToMessage();
    }

    private Message resolveContextMessage() {
        return cognitionCapability.contextWorkspace().resolve(List.of(
                ContextBlock.FocusedDomain.ACTION,
                ContextBlock.FocusedDomain.COGNITION,
                ContextBlock.FocusedDomain.MEMORY
        )).encodeToMessage();
    }

    @NotNull
    @Override
    public String modelKey() {
        return "action_correction_recognizer";
    }
}
