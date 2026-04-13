package work.slhaf.partner.module.memory.selector.extractor;

import kotlin.Unit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.TaskBlock;
import work.slhaf.partner.module.memory.runtime.MemoryRuntime;
import work.slhaf.partner.module.memory.selector.extractor.entity.ExtractorInput;
import work.slhaf.partner.module.memory.selector.extractor.entity.ExtractorMatchData;
import work.slhaf.partner.module.memory.selector.extractor.entity.ExtractorResult;

import java.time.format.DateTimeFormatter;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemorySelectExtractor extends AbstractAgentModule.Sub<ExtractorInput, ExtractorResult> implements ActivateModel {
    @InjectCapability
    private CognitionCapability cognitionCapability;
    @InjectModule
    private MemoryRuntime memoryRuntime;

    @Override
    protected ExtractorResult doExecute(ExtractorInput input) {
        ExtractorResult extractorResult;
        List<Message> messages = List.of(
                resolveContextMessage(),
                resolveTaskMessage(input)
        );
        Result<ExtractorResult> result = formattedChat(
                messages,
                ExtractorResult.class
        );
        extractorResult = result.fold(
                value -> value,
                exception -> {
                    ExtractorResult fallback = new ExtractorResult();
                    fallback.setMatches(List.of());
                    return fallback;
                }
        );
        return fix(extractorResult);
    }

    private Message resolveTaskMessage(ExtractorInput input) {
        return new TaskBlock() {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendListElement(document, root, "new_inputs", "input", input.getInputs().entrySet(), (inputElement, input) -> {
                    inputElement.setAttribute("datetime", input.getKey().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    inputElement.setTextContent(input.getValue());
                    return Unit.INSTANCE;
                });
                appendTextElement(document, root, "current_date", input.getDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                appendTextElement(document, root, "memory_topic_tree", input.getTopic_tree());
            }
        }.encodeToMessage();
    }

    private Message resolveContextMessage() {
        return cognitionCapability.contextWorkspace().resolve(List.of(
                ContextBlock.VisibleDomain.COGNITION, ContextBlock.VisibleDomain.MEMORY
        )).encodeToMessage();
    }

    private ExtractorResult fix(ExtractorResult extractorResult) {
        extractorResult.getMatches().forEach(m -> {
            if (m.getType().equals(ExtractorMatchData.Constant.DATE)) {
                return;
            }
            m.setText(memoryRuntime.fixTopicPath(m.getText()));
        });
        if (extractorResult.getMatches().isEmpty()) {
            return extractorResult;
        }
        extractorResult.getMatches().removeIf(m -> m.getText().split("->")[0].isEmpty());
        return extractorResult;
    }

    @Override
    public String modelKey() {
        return "topic_extractor";
    }
}
