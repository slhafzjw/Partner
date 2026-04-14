package work.slhaf.partner.module.memory.updater;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;
import work.slhaf.partner.framework.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.TaskBlock;
import work.slhaf.partner.module.communication.AfterRolling;
import work.slhaf.partner.module.communication.AfterRollingRegistry;
import work.slhaf.partner.module.communication.RollingResult;
import work.slhaf.partner.module.memory.runtime.MemoryRuntime;
import work.slhaf.partner.module.memory.updater.summarizer.entity.MemoryTopicResult;

import java.util.List;

public class MemoryUpdater extends AbstractAgentModule.Standalone implements AfterRolling, ActivateModel {

    @InjectCapability
    private CognitionCapability cognitionCapability;

    @InjectModule
    private MemoryRuntime memoryRuntime;
    @InjectModule
    private AfterRollingRegistry afterRollingRegistry;

    @Init
    public void init() {
        afterRollingRegistry.register(this);
    }

    @Override
    public void consume(RollingResult result) {
        List<Message> slicedMessages = sliceMessages(result);
        if (slicedMessages.isEmpty()) {
            return;
        }
        Result<MemoryTopicResult> extractResult = formattedChat(
                List.of(
                        cognitionCapability.contextWorkspace().resolve(List.of(
                                ContextBlock.VisibleDomain.COGNITION,
                                ContextBlock.VisibleDomain.MEMORY
                        )).encodeToMessage(),
                        resolveTopicTaskMessage(result, slicedMessages)
                ),
                MemoryTopicResult.class
        );
        extractResult.onSuccess(topicResult -> {
            String topicPath = topicResult.getTopicPath() == null ? null : memoryRuntime.fixTopicPath(topicResult.getTopicPath());
            List<String> relatedTopicPaths = topicResult.getRelatedTopicPaths() == null
                    ? List.of()
                    : topicResult.getRelatedTopicPaths().stream().map(memoryRuntime::fixTopicPath).toList();
            memoryRuntime.recordMemory(result.memoryUnit(), topicPath, relatedTopicPaths);
        }).onFailure(exp -> memoryRuntime.recordMemory(result.memoryUnit(), null, List.of()));
    }

    private List<Message> sliceMessages(RollingResult result) {
        int size = result.memoryUnit().getConversationMessages().size();
        int start = Math.clamp(result.memorySlice().getStartIndex(), 0, size);
        int end = Math.clamp(result.memorySlice().getEndIndex(), start, size);
        if (start >= end) {
            return List.of();
        }
        return result.memoryUnit().getConversationMessages().subList(start, end);
    }

    private Message resolveTopicTaskMessage(RollingResult result, List<Message> slicedMessages) {
        return new TaskBlock() {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendTextElement(document, root, "current_topic_tree", memoryRuntime.getTopicTree());
                appendTextElement(document, root, "slice_summary", result.summary());
                appendRepeatedElements(document, root, "message", slicedMessages, (messageElement, message) -> {
                    messageElement.setAttribute("role", message.roleValue());
                    messageElement.setTextContent(message.getContent());
                    return kotlin.Unit.INSTANCE;
                });
            }
        }.encodeToMessage();
    }

    @Override
    @NotNull
    public String modelKey() {
        return "topic_extractor";
    }
}
