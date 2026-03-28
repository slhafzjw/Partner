package work.slhaf.partner.module.memory.selector;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.memory.exception.UnExistedDateIndexException;
import work.slhaf.partner.core.memory.exception.UnExistedTopicException;
import work.slhaf.partner.core.memory.pojo.ActivatedMemorySlice;
import work.slhaf.partner.module.memory.runtime.MemoryRuntime;
import work.slhaf.partner.module.memory.selector.evaluator.SliceSelectEvaluator;
import work.slhaf.partner.module.memory.selector.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.memory.selector.extractor.MemorySelectExtractor;
import work.slhaf.partner.module.memory.selector.extractor.entity.ExtractorInput;
import work.slhaf.partner.module.memory.selector.extractor.entity.ExtractorMatchData;
import work.slhaf.partner.module.memory.selector.extractor.entity.ExtractorResult;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

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
        // TODO
    }

    private List<ActivatedMemorySlice> selectAndEvaluateMemory(Map<LocalDateTime, String> snapshotInputs, ExtractorResult extractorResult) {
        log.debug("[MemorySelector] 触发记忆回溯...");
        LinkedHashMap<String, ActivatedMemorySlice> candidates = new LinkedHashMap<>();
        setMemoryCandidates(candidates, extractorResult.getMatches());
        removeDuplicateSlice(candidates.values());
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

    private void removeDuplicateSlice(Collection<ActivatedMemorySlice> candidates) {
        candidates.removeIf(m -> memoryRuntime.containsDialogSummary(m.getSummary()));
    }

    @Override
    public int order() {
        return 2;
    }
}
