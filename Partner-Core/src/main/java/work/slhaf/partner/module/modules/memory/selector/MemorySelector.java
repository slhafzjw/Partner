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
import work.slhaf.partner.core.memory.pojo.EvaluatedSlice;
import work.slhaf.partner.core.memory.pojo.MemoryResult;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.module.modules.memory.selector.evaluator.SliceSelectEvaluator;
import work.slhaf.partner.module.modules.memory.selector.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.modules.memory.selector.extractor.MemorySelectExtractor;
import work.slhaf.partner.module.modules.memory.selector.extractor.entity.ExtractorMatchData;
import work.slhaf.partner.module.modules.memory.selector.extractor.entity.ExtractorResult;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemorySelector extends AbstractAgentModule.Running<PartnerRunningFlowContext> {
    @InjectCapability
    private MemoryCapability memoryCapability;
    @InjectCapability
    private CognationCapability cognationCapability;
    @InjectModule
    private SliceSelectEvaluator sliceSelectEvaluator;
    @InjectModule
    private MemorySelectExtractor memorySelectExtractor;

    @Override
    public void execute(PartnerRunningFlowContext runningFlowContext) {
        String userId = runningFlowContext.getSource();
        //获取主题路径
        ExtractorResult extractorResult = memorySelectExtractor.execute(runningFlowContext);
        if (extractorResult.isRecall() || !extractorResult.getMatches().isEmpty()) {
            memoryCapability.clearActivatedSlices(userId);
            List<EvaluatedSlice> evaluatedSlices = selectAndEvaluateMemory(runningFlowContext, extractorResult);
            memoryCapability.updateActivatedSlices(userId, evaluatedSlices);
        }
        setModuleContextRecall(runningFlowContext);
    }

    private List<EvaluatedSlice> selectAndEvaluateMemory(PartnerRunningFlowContext runningFlowContext, ExtractorResult extractorResult) {
        log.debug("[MemorySelector] 触发记忆回溯...");
        //查找切片
        String userId = runningFlowContext.getSource();
        List<MemoryResult> memoryResultList = new ArrayList<>();
        setMemoryResultList(memoryResultList, extractorResult.getMatches(), userId);
        //评估切片
        EvaluatorInput evaluatorInput = EvaluatorInput.builder()
                .input(runningFlowContext.getInput())
                .memoryResults(memoryResultList)
                .messages(cognationCapability.getChatMessages())
                .build();
        log.debug("[MemorySelector] 切片评估输入: {}", JSONObject.toJSONString(evaluatorInput));
        List<EvaluatedSlice> memorySlices = sliceSelectEvaluator.execute(evaluatorInput);
        log.debug("[MemorySelector] 切片评估结果: {}", JSONObject.toJSONString(memorySlices));
        return memorySlices;
    }

    private void setModuleContextRecall(PartnerRunningFlowContext runningFlowContext) {
        String userId = runningFlowContext.getSource();
        boolean recall = memoryCapability.hasActivatedSlices(userId);
        runningFlowContext.getModuleContext().getExtraContext().put("recall", recall);
        if (recall) {
            runningFlowContext.getModuleContext().getExtraContext().put("recall_count", memoryCapability.getActivatedSlicesSize(userId));
        }
    }

    private void setMemoryResultList(List<MemoryResult> memoryResultList, List<ExtractorMatchData> matches, String userId) {
        for (ExtractorMatchData match : matches) {
            try {
                MemoryResult memoryResult = switch (match.getType()) {
                    case ExtractorMatchData.Constant.TOPIC -> memoryCapability.selectMemory(match.getText());
                    case ExtractorMatchData.Constant.DATE ->
                            memoryCapability.selectMemory(LocalDate.parse(match.getText()));
                    default -> null;
                };
                if (memoryResult == null || memoryResult.isEmpty()) continue;
                removeDuplicateSlice(memoryResult);
                memoryResultList.add(memoryResult);
            } catch (UnExistedDateIndexException | UnExistedTopicException e) {
                log.error("[MemorySelector] 不存在的记忆索引! 请尝试更换更合适的主题提取LLM!", e);
                log.error("[MemorySelector] 错误索引: {}", match.getText());
            }
        }
        //清理切片记录
        memoryCapability.cleanSelectedSliceFilter();
        //根据userInfo过滤是否为私人记忆
        for (MemoryResult memoryResult : memoryResultList) {
            //过滤终点记忆
            memoryResult.getMemorySliceResult().removeIf(m -> removeOrNot(m.getMemorySlice(), userId));
            //过滤邻近记忆
            memoryResult.getRelatedMemorySliceResult().removeIf(m -> removeOrNot(m, userId));
        }
    }

    private void removeDuplicateSlice(MemoryResult memoryResult) {
        Collection<String> values = memoryCapability.getDialogMap().values();
        memoryResult.getRelatedMemorySliceResult().removeIf(m -> values.contains(m.getSummary()));
        memoryResult.getMemorySliceResult().removeIf(m -> values.contains(m.getMemorySlice().getSummary()));
    }

    private boolean removeOrNot(MemorySlice memorySlice, String userId) {
        if (memorySlice.isPrivate()) {
            return memorySlice.getStartUserId().equals(userId);
        }
        return false;
    }

    @Override
    public int order() {
        return 2;
    }
}
