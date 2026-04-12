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

    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    public @NotNull Result<ExtractorResult> execute(String input) {
        List<Message> messages = List.of(
                cognitionCapability.contextWorkspace().resolve(List.of(
                        ContextBlock.VisibleDomain.COGNITION,
                        ContextBlock.VisibleDomain.ACTION
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

    @NotNull
    @Override
    public String modelKey() {
        return "action_extractor";
    }
}
