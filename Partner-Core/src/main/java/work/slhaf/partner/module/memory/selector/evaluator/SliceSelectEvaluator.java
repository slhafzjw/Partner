package work.slhaf.partner.module.memory.selector.evaluator;

import kotlin.Unit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;
import work.slhaf.partner.framework.agent.model.ActivateModel;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.module.TaskBlock;
import work.slhaf.partner.module.memory.selector.ActivatedMemorySlice;
import work.slhaf.partner.module.memory.selector.evaluator.entity.EvaluatorBatchInput;
import work.slhaf.partner.module.memory.selector.evaluator.entity.EvaluatorBatchResult;
import work.slhaf.partner.module.memory.selector.evaluator.entity.EvaluatorInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

@EqualsAndHashCode(callSuper = true)
@Data
public class SliceSelectEvaluator extends AbstractAgentModule.Sub<EvaluatorInput, List<ActivatedMemorySlice>> implements ActivateModel {

    @InjectCapability
    private ActionCapability actionCapability;
    @InjectCapability
    private CognitionCapability cognitionCapability;

    private ExecutorService executor;

    @Init
    public void init() {
        executor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
    }

    @Override
    protected List<ActivatedMemorySlice> doExecute(EvaluatorInput evaluatorInput) {
        List<ActivatedMemorySlice> preparedSlices = evaluatorInput.getMemorySlices();
        List<ActivatedMemorySlice> result = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(preparedSlices.size());

        Message contextMessage = cognitionCapability.contextWorkspace().resolve(List.of(
                ContextBlock.FocusedDomain.MEMORY
        )).encodeToMessage();

        for (ActivatedMemorySlice slice : preparedSlices) {
            executor.execute(() -> {
                try {
                    EvaluatorBatchInput batchInput = new EvaluatorBatchInput(evaluatorInput.getInputs(), slice);
                    List<Message> messages = List.of(
                            contextMessage,
                            resolveTaskMessage(batchInput)
                    );
                    formattedChat(messages, EvaluatorBatchResult.class)
                            .onSuccess(evaluatorBatchResult -> {
                                if (evaluatorBatchResult.isPassed()) {
                                    synchronized (result) {
                                        result.add(slice);
                                    }
                                }
                            });
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }

        return result;
    }

    private Message resolveTaskMessage(EvaluatorBatchInput batchInput) {
        return new TaskBlock() {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendChildElement(document, root, "new_inputs", (inputsElement) -> {
                    appendListElement(document, inputsElement, "inputs", "input", batchInput.getInputs(), (inputElement, entry) -> {
                        inputElement.setAttribute("interval-to-first", String.valueOf(entry.getOffsetMillis()));
                        inputElement.setTextContent(entry.getContent());
                        return Unit.INSTANCE;
                    });
                    return Unit.INSTANCE;
                });
                appendChildElement(document, root, "memory_slice", (element) -> {
                    ActivatedMemorySlice slice = batchInput.getActivatedMemorySlice();
                    appendTextElement(document, element, "slice_summary", slice.getSummary());
                    appendChildElement(document, element, "source_messages", (messagesElement) -> {
                        List<Message> messages = slice.getMessages();
                        int size = messages.size();
                        if (size > 10) {
                            // 展示前两条
                            appendMessageElement(document, messagesElement, messages.subList(0, 2));

                            // 中间省略说明
                            int middleStart = Math.max(2, (size - 4) / 2);
                            int middleEnd = Math.min(size - 2, middleStart + 4);
                            int omittedBeforeMiddle = middleStart - 2;
                            appendTextElement(document, messagesElement, "omitted_messages", "省略了 " + omittedBeforeMiddle + " 条消息");

                            // 中间四条
                            appendMessageElement(document, messagesElement, messages.subList(middleStart, middleEnd));

                            // 中间到结尾前的省略说明
                            int omittedAfterMiddle = (size - 2) - middleEnd;
                            if (omittedAfterMiddle > 0) {
                                appendTextElement(document, messagesElement, "omitted_messages", "省略了 " + omittedAfterMiddle + " 条消息");
                            }

                            // 展示末尾两条
                            appendMessageElement(document, messagesElement, messages.subList(size - 2, size));
                        } else {
                            appendMessageElement(document, messagesElement, messages);
                        }
                        return Unit.INSTANCE;
                    });
                    return Unit.INSTANCE;
                });
            }

            private void appendMessageElement(Document document, Element parent, List<Message> messages) {
                appendRepeatedElements(document, parent, "message", messages, (messageElement, message) -> {
                    messageElement.setAttribute("role", message.getRole().name().toLowerCase(Locale.ROOT));
                    messageElement.setTextContent(message.getContent());
                    return Unit.INSTANCE;
                });
            }
        }.encodeToMessage();
    }

    @Override
    @NotNull
    public String modelKey() {
        return "slice_evaluator";
    }
}
