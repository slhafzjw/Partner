package work.slhaf.partner.module.communication;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.model.ActivateModel;
import work.slhaf.partner.api.agent.model.pojo.Message;
import work.slhaf.partner.core.cognition.BlockContent;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.module.TaskBlock;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DialogRollingService extends AbstractAgentModule.Standalone implements ActivateModel {

    @InjectCapability
    private CognitionCapability cognitionCapability;

    public void rollMessages(List<Message> snapshotMessages, int rollingSize, int retainSize) {
        rollMessages(snapshotMessages, rollingSize, retainSize, null, null, null);
    }

    public void rollMessages(List<Message> snapshotMessages, int rollingSize, int retainSize, @Nullable String unitId, @Nullable String sliceId, @Nullable String summary) {
        summary = summary == null ? summarize(snapshotMessages) : summary;
        cognitionCapability.contextWorkspace().register(new ContextBlock(
                buildDialogAbstractBlock(summary, unitId, sliceId),
                Set.of(ContextBlock.VisibleDomain.MEMORY, ContextBlock.VisibleDomain.COMMUNICATION),
                35,
                8,
                10
        ));
        cognitionCapability.rollChatMessagesWithSnapshot(rollingSize, retainSize);
    }

    private String summarize(List<Message> snapshotMessages) {
        List<Message> messages = List.of(
                resolveTaskBlock(snapshotMessages)
        );
        return chat(messages);
    }

    private @NotNull BlockContent buildDialogAbstractBlock(String summary, @Nullable String unitId, @Nullable String sliceId) {
        return new BlockContent("dialog_history", "dialog_rolling_service") {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                if (unitId != null) root.setAttribute("related_memory_unit_id", unitId);
                if (sliceId != null) root.setAttribute("related_memory_slice_id", sliceId);
                root.setAttribute("datetime", ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

                appendTextElement(document, root, "summary", summary);
            }
        };
    }

    private Message resolveTaskBlock(List<Message> snapshotMessages) {
        return new TaskBlock() {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendRepeatedElements(document, root, "message", snapshotMessages, (messageElement, message) -> {
                    messageElement.setAttribute("role", message.getRole().name().toLowerCase(Locale.ROOT));
                    messageElement.setTextContent(message.getContent());
                    return Unit.INSTANCE;
                });
            }
        }.encodeToMessage();
    }

}
