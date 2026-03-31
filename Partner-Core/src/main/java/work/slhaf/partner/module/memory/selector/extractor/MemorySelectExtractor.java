package work.slhaf.partner.module.memory.selector.extractor;

import kotlin.Unit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.api.agent.model.ActivateModel;
import work.slhaf.partner.api.agent.model.pojo.Message;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.module.TaskBlock;
import work.slhaf.partner.module.memory.runtime.MemoryRuntime;
import work.slhaf.partner.module.memory.selector.extractor.entity.ExtractorInput;
import work.slhaf.partner.module.memory.selector.extractor.entity.ExtractorMatchData;
import work.slhaf.partner.module.memory.selector.extractor.entity.ExtractorResult;

import java.time.format.DateTimeFormatter;
import java.util.List;

import static work.slhaf.partner.common.util.ExtractUtil.fixTopicPath;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemorySelectExtractor extends AbstractAgentModule.Sub<ExtractorInput, ExtractorResult> implements ActivateModel {
    @InjectCapability
    private CognitionCapability cognitionCapability;
    @InjectModule
    private MemoryRuntime memoryRuntime;

    @Override
    public ExtractorResult execute(ExtractorInput input) {
        log.debug("[MemorySelectExtractor] 主题提取模块开始...");
        ExtractorResult extractorResult;
        try {
            List<Message> messages = List.of(
                    resolveContextMessage(),
                    resolveTaskMessage(input)
            );
            extractorResult = formattedChat(
                    messages,
                    ExtractorResult.class
            );
            log.debug("[MemorySelectExtractor] 主题提取结果: {}", extractorResult);
        } catch (Exception e) {
            log.error("[MemorySelectExtractor] 主题提取出错: ", e);
            extractorResult = new ExtractorResult();
            extractorResult.setRecall(false);
            extractorResult.setMatches(List.of());
        }
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
            m.setText(fixTopicPath(m.getText()));
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
