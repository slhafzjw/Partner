package work.slhaf.partner.module.modules.action.planner.extractor;

import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.abstracts.ActivateModel;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.module.modules.action.planner.extractor.entity.ExtractorResult;

import java.util.List;

public class ActionExtractor extends AbstractAgentModule.Sub<String, ExtractorResult> implements ActivateModel {
    @InjectCapability
    private ActionCapability actionCapability;
    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    public ExtractorResult execute(String input) {
        List<String> tendencyCache = actionCapability.selectTendencyCache(input);
        if (tendencyCache != null && !tendencyCache.isEmpty()) {
            ExtractorResult result = new ExtractorResult();
            result.setTendencies(tendencyCache);
            return result;
        }
        for (int i = 0; i < 3; i++) {
            try {
                List<Message> messages = List.of(
                        cognitionCapability.contextWorkspace().resolve(List.of(
                                ContextBlock.VisibleDomain.ACTION,
                                ContextBlock.VisibleDomain.COGNITION
                        )).encodeToContextMessage(),
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
