package work.slhaf.partner.module.action.planner.extractor;

import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.action.planner.extractor.entity.ExtractorResult;

import java.util.List;

public class ActionExtractor extends AbstractAgentModule.Sub<String, ExtractorResult> implements ActivateModel {

    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    public ExtractorResult execute(String input) {
        List<Message> messages = List.of(
                cognitionCapability.contextWorkspace().resolve(List.of(
                        ContextBlock.VisibleDomain.COGNITION,
                        ContextBlock.VisibleDomain.ACTION
                )).encodeToMessage(),
                new Message(Message.Character.USER, input)
        );
        Result<ExtractorResult> result = formattedChat(messages, ExtractorResult.class);
        if (result.isSuccess()) {
            return result.getOrThrow();
        }
        log.error("提取信息出错", result.exceptionOrNull());
        return new ExtractorResult();
    }

    @NotNull
    @Override
    public String modelKey() {
        return "action_extractor";
    }
}
