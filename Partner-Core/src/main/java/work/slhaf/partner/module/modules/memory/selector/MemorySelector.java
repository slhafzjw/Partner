package work.slhaf.partner.module.modules.memory.selector;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.exception.UnExistedDateIndexException;
import work.slhaf.partner.core.memory.exception.UnExistedTopicException;
import work.slhaf.partner.core.memory.pojo.ActivatedMemorySlice;
import work.slhaf.partner.module.modules.memory.runtime.MemoryRuntime;
import work.slhaf.partner.module.modules.memory.selector.evaluator.SliceSelectEvaluator;
import work.slhaf.partner.module.modules.memory.selector.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.modules.memory.selector.extractor.MemorySelectExtractor;
import work.slhaf.partner.module.modules.memory.selector.extractor.entity.ExtractorMatchData;
import work.slhaf.partner.module.modules.memory.selector.extractor.entity.ExtractorResult;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemorySelector extends AbstractAgentModule.Running<PartnerRunningFlowContext> {
    @InjectCapability
    private MemoryCapability memoryCapability;
    @InjectCapability
    private CognationCapability cognationCapability;
    @InjectModule
    private MemoryRuntime memoryRuntime;
    @InjectModule
    private SliceSelectEvaluator sliceSelectEvaluator;
    @InjectModule
    private MemorySelectExtractor memorySelectExtractor;

    @Override
    public void execute(PartnerRunningFlowContext runningFlowContext) {
        ExtractorResult extractorResult = memorySelectExtractor.execute(runningFlowContext);
        if (extractorResult.isRecall() || !extractorResult.getMatches().isEmpty()) {
            memoryCapability.clearActivatedSlices();
            List<ActivatedMemorySlice> activatedSlices = selectAndEvaluateMemory(runningFlowContext, extractorResult);
            memoryCapability.updateActivatedSlices(activatedSlices);
        }
        setModuleContextRecall(runningFlowContext);
    }

    private List<ActivatedMemorySlice> selectAndEvaluateMemory(PartnerRunningFlowContext runningFlowContext,
                                                               ExtractorResult extractorResult) {
        log.debug("[MemorySelector] 触发记忆回溯...");
        LinkedHashMap<String, ActivatedMemorySlice> candidates = new LinkedHashMap<>();
        setMemoryCandidates(candidates, extractorResult.getMatches());
        removeDuplicateSlice(candidates.values());
        EvaluatorInput evaluatorInput = EvaluatorInput.builder()
                .input(runningFlowContext.getInput())
                .memorySlices(new ArrayList<>(candidates.values()))
                .messages(cognationCapability.getChatMessages())
                .build();
        log.debug("[MemorySelector] 切片评估输入: {}", JSONObject.toJSONString(evaluatorInput));
        List<ActivatedMemorySlice> memorySlices = sliceSelectEvaluator.execute(evaluatorInput);
        log.debug("[MemorySelector] 切片评估结果: {}", JSONObject.toJSONString(memorySlices));
        return memorySlices;
    }

    private void setModuleContextRecall(PartnerRunningFlowContext runningFlowContext) {
        boolean recall = memoryCapability.hasActivatedSlices();
        runningFlowContext.getModuleContext().getExtraContext().put("recall", recall);
        if (recall) {
            runningFlowContext.getModuleContext().getExtraContext().put("recall_count", memoryCapability.getActivatedSlicesSize());
        }
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
