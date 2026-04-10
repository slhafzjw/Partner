package work.slhaf.partner.module.memory.selector;

import kotlin.Unit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.cognition.BlockContent;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.framework.agent.model.pojo.Message;
import work.slhaf.partner.module.memory.runtime.MemoryRuntime;
import work.slhaf.partner.module.memory.runtime.exception.UnExistedDateIndexException;
import work.slhaf.partner.module.memory.runtime.exception.UnExistedTopicException;
import work.slhaf.partner.module.memory.selector.evaluator.SliceSelectEvaluator;
import work.slhaf.partner.module.memory.selector.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.memory.selector.extractor.MemorySelectExtractor;
import work.slhaf.partner.module.memory.selector.extractor.entity.ExtractorInput;
import work.slhaf.partner.module.memory.selector.extractor.entity.ExtractorMatchData;
import work.slhaf.partner.module.memory.selector.extractor.entity.ExtractorResult;
import work.slhaf.partner.runtime.PartnerRunningFlowContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemorySelector extends AbstractAgentModule.Running<PartnerRunningFlowContext> {

    private static final String BLOCK_NAME = "activated_memory_slices";
    private static final String SOURCE = "memory_selector";

    @InjectCapability
    private CognitionCapability cognitionCapability;
    @InjectCapability
    private ActionCapability actionCapability;

    @InjectModule
    private MemoryRuntime memoryRuntime;
    @InjectModule
    private SliceSelectEvaluator sliceSelectEvaluator;
    @InjectModule
    private MemorySelectExtractor memorySelectExtractor;

    private AtomicBoolean memoryCalling = new AtomicBoolean(false);
    private Map<LocalDateTime, String> collectedInputs = new HashMap<>();
    private Lock inputsLock = new ReentrantLock();

    @Override
    public void execute(@NotNull PartnerRunningFlowContext runningFlowContext) {
        inputsLock.lock();
        try {
            collectedInputs.put(ZonedDateTime.now().toLocalDateTime(), runningFlowContext.getInput());
        } finally {
            inputsLock.unlock();
        }

        tryStartMemoryRecallWorker();
    }

    private void tryStartMemoryRecallWorker() {
        if (!memoryCalling.compareAndSet(false, true)) {
            return;
        }

        actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL).execute(() -> {
            try {
                drainMemoryRecall();
            } finally {
                memoryCalling.set(false);

                // 防止竞态：worker 退出前后，刚好来了新输入，但没有线程负责再拉起 worker
                if (!collectedInputs.isEmpty()) {
                    tryStartMemoryRecallWorker();
                }
            }
        });
    }

    private void drainMemoryRecall() {
        while (true) {
            Map<LocalDateTime, String> snapshotInputs;

            inputsLock.lock();
            try {
                if (collectedInputs.isEmpty()) {
                    return;
                }
                snapshotInputs = new HashMap<>(collectedInputs);
                collectedInputs.clear();
            } finally {
                inputsLock.unlock();
            }

            ExtractorInput input = new ExtractorInput(
                    snapshotInputs,
                    memoryRuntime.getTopicTree(),
                    snapshotInputs.keySet()
                            .stream()
                            .max(LocalDateTime::compareTo)
                            .orElseThrow()
                            .toLocalDate()
            );

            ExtractorResult extractorResult = memorySelectExtractor.execute(input);
            if (extractorResult.isRecall() || !extractorResult.getMatches().isEmpty()) {
                List<ActivatedMemorySlice> activatedSlices = selectAndEvaluateMemory(snapshotInputs, extractorResult);
                updateMemoryContext(activatedSlices);
            }
        }
    }

    private void updateMemoryContext(List<ActivatedMemorySlice> activatedSlices) {
        cognitionCapability.contextWorkspace().register(new ContextBlock(
                buildMemoryFullBlock(activatedSlices),
                buildMemoryCompactBlock(activatedSlices),
                buildMemoryAbstractBlock(activatedSlices),
                Set.of(ContextBlock.VisibleDomain.MEMORY),
                18,
                8,
                16
        ));
    }

    private @NotNull BlockContent buildMemoryAbstractBlock(List<ActivatedMemorySlice> activatedSlices) {
        return new MemoryBlock(activatedSlices) {
            @Override
            protected void fillSliceElement(Document document, @NotNull Element sliceElement, ActivatedMemorySlice slice) {
                appendTextElement(document, sliceElement, "summary", slice.getSummary());
            }
        };
    }

    private @NotNull BlockContent buildMemoryCompactBlock(List<ActivatedMemorySlice> activatedSlices) {
        return new MemoryBlock(activatedSlices) {
            @Override
            protected void fillSliceElement(Document document, @NotNull Element sliceElement, ActivatedMemorySlice slice) {
                appendTextElement(document, sliceElement, "slice_summary", slice.getSummary());
                appendChildElement(document, sliceElement, "source_messages", (messagesElement) -> {
                    List<Message> messages = slice.getMessages();
                    int size = messages.size();
                    if (size > 10) {
                        int middleStart = Math.max(2, (size - 4) / 2);
                        int middleEnd = Math.min(size - 2, middleStart + 4);
                        int omittedBeforeMiddle = middleStart - 2;
                        appendTextElement(document, messagesElement, "omitted_messages", "省略了 " + omittedBeforeMiddle + " 条消息");

                        appendMessageElement(document, messagesElement, messages.subList(middleStart, middleEnd));

                        int omittedAfterMiddle = (size - 2) - middleEnd;
                        if (omittedAfterMiddle > 0) {
                            appendTextElement(document, messagesElement, "omitted_messages", "省略了 " + omittedAfterMiddle + " 条消息");
                        }

                        appendMessageElement(document, messagesElement, messages.subList(size - 2, size));
                    } else {
                        appendMessageElement(document, messagesElement, messages);
                    }
                    return Unit.INSTANCE;
                });
            }
        };
    }

    private @NotNull BlockContent buildMemoryFullBlock(List<ActivatedMemorySlice> activatedSlices) {
        return new MemoryBlock(activatedSlices) {
            @Override
            protected void fillSliceElement(Document document, @NotNull Element sliceElement, ActivatedMemorySlice slice) {
                appendTextElement(document, sliceElement, "slice_summary", slice.getSummary());
                appendChildElement(document, sliceElement, "source_messages", (messagesElement) -> {
                    List<Message> messages = slice.getMessages();
                    int size = messages.size();
                    if (size > 10) {
                        appendMessageElement(document, messagesElement, messages.subList(0, 2));

                        int middleStart = Math.max(2, (size - 4) / 2);
                        int middleEnd = Math.min(size - 2, middleStart + 4);
                        int omittedBeforeMiddle = middleStart - 2;
                        appendTextElement(document, messagesElement, "omitted_messages", "省略了 " + omittedBeforeMiddle + " 条消息");

                        appendMessageElement(document, messagesElement, messages.subList(middleStart, middleEnd));

                        int omittedAfterMiddle = (size - 2) - middleEnd;
                        if (omittedAfterMiddle > 0) {
                            appendTextElement(document, messagesElement, "omitted_messages", "省略了 " + omittedAfterMiddle + " 条消息");
                        }

                        appendMessageElement(document, messagesElement, messages.subList(size - 2, size));
                    } else {
                        appendMessageElement(document, messagesElement, messages);
                    }
                    return Unit.INSTANCE;
                });
            }
        };
    }

    private List<ActivatedMemorySlice> selectAndEvaluateMemory(Map<LocalDateTime, String> snapshotInputs, ExtractorResult extractorResult) {
        log.debug("[MemorySelector] 触发记忆回溯...");
        LinkedHashMap<String, ActivatedMemorySlice> candidates = new LinkedHashMap<>();
        setMemoryCandidates(candidates, extractorResult.getMatches());
        EvaluatorInput evaluatorInput = EvaluatorInput.builder()
                .inputs(snapshotInputs)
                .memorySlices(new ArrayList<>(candidates.values()))
                .build();
        return sliceSelectEvaluator.execute(evaluatorInput);
    }

    private void setMemoryCandidates(LinkedHashMap<String, ActivatedMemorySlice> candidates,
                                     List<ExtractorMatchData> matches) {
        for (ExtractorMatchData match : matches) {
            try {
                List<ActivatedMemorySlice> recalledSlices = switch (match.getType()) {
                    case ExtractorMatchData.Constant.TOPIC ->
                            memoryRuntime.queryActivatedMemoryByTopicPath(match.getText());
                    case ExtractorMatchData.Constant.DATE ->
                            memoryRuntime.queryActivatedMemoryByDate(LocalDate.parse(match.getText()));
                    default -> List.of();
                };
                for (ActivatedMemorySlice recalledSlice : recalledSlices) {
                    candidates.putIfAbsent(recalledSlice.getUnitId() + ":" + recalledSlice.getSliceId(), recalledSlice);
                }
            } catch (UnExistedDateIndexException | UnExistedTopicException e) {
                log.error("[MemorySelector] 不存在的记忆索引", e);
                log.error("[MemorySelector] 错误索引: {}", match.getText());
            }
        }
    }

    @Override
    public int order() {
        return 2;
    }

    abstract static class MemoryBlock extends BlockContent {

        private final List<ActivatedMemorySlice> activatedSlices;

        protected MemoryBlock(List<ActivatedMemorySlice> activatedSlices) {
            super(BLOCK_NAME, SOURCE);
            this.activatedSlices = activatedSlices;
        }

        @Override
        protected void fillXml(@NotNull Document document, @NotNull Element root) {
            appendRepeatedElements(document, root, "memory_slice", activatedSlices, (sliceElement, slice) -> {
                sliceElement.setAttribute("unit_id", slice.getUnitId());
                sliceElement.setAttribute("slice_id", slice.getSliceId());
                fillSliceElement(document, sliceElement, slice);
                return Unit.INSTANCE;
            });
        }

        protected void appendMessageElement(Document document, Element parent, List<Message> messages) {
            appendRepeatedElements(document, parent, "message", messages, (messageElement, message) -> {
                messageElement.setAttribute("role", message.getRole().name().toLowerCase(Locale.ROOT));
                messageElement.setTextContent(message.getContent());
                return Unit.INSTANCE;
            });
        }

        protected abstract void fillSliceElement(Document document, @NotNull Element sliceElement, ActivatedMemorySlice slice);
    }
}
