package work.slhaf.partner.module.action.planner.extractor;

import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.model.ActivateModel;
import work.slhaf.partner.api.agent.model.pojo.Message;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.module.action.planner.extractor.entity.ExtractorResult;

import java.util.List;

public class ActionExtractor extends AbstractAgentModule.Sub<String, ExtractorResult> implements ActivateModel {

    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    public ExtractorResult execute(String input) {
        for (int i = 0; i < 3; i++) {
            try {
                List<Message> messages = List.of(
                        cognitionCapability.contextWorkspace().resolve(List.of(
                                ContextBlock.VisibleDomain.COGNITION,
                                ContextBlock.VisibleDomain.ACTION
                        )).encodeToMessage(),
                        new Message(Message.Character.USER, input)
                );
                return formattedChat(
                        messages,
                        ExtractorResult.class
                );
            } catch (Exception e) {
                log.error("提取信息出错", e);
            }
        }
        return new ExtractorResult();
    }

    @NotNull
    @Override
    public String modelKey() {
        return "action_extractor";
    }
}
