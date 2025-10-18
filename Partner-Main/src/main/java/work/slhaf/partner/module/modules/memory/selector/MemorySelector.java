package work.slhaf.partner.module.modules.memory.selector;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.exception.UnExistedDateIndexException;
import work.slhaf.partner.core.memory.exception.UnExistedTopicException;
import work.slhaf.partner.core.memory.pojo.EvaluatedSlice;
import work.slhaf.partner.core.memory.pojo.MemoryResult;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.module.common.module.PreRunningModule;
import work.slhaf.partner.module.modules.memory.selector.evaluator.SliceSelectEvaluator;
import work.slhaf.partner.module.modules.memory.selector.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.modules.memory.selector.extractor.MemorySelectExtractor;
import work.slhaf.partner.module.modules.memory.selector.extractor.entity.ExtractorMatchData;
import work.slhaf.partner.module.modules.memory.selector.extractor.entity.ExtractorResult;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
@AgentModule(name = "memory_selector", order = 2)
public class MemorySelector extends PreRunningModule {

    @InjectCapability
    private MemoryCapability memoryCapability;
    @InjectCapability
    private CognationCapability cognationCapability;

    @InjectModule
    private SliceSelectEvaluator sliceSelectEvaluator;
    @InjectModule
    private MemorySelectExtractor memorySelectExtractor;

    @Override
    public void doExecute(PartnerRunningFlowContext runningFlowContext) {
        String userId = runningFlowContext.getUserId();
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
        String userId = runningFlowContext.getUserId();
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
        String userId = runningFlowContext.getUserId();
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
    public String moduleName() {
        return "[记忆模块]";
    }

    @Override
    protected HashMap<String, String> getPromptDataMap(PartnerRunningFlowContext context) {
        HashMap<String, String> map = new HashMap<>();
        String userId = context.getUserId();
        String dialogMapStr = memoryCapability.getDialogMapStr();
        if (!dialogMapStr.isEmpty()) {
            map.put("[记忆缓存] <你最近两日和所有聊天者的对话记忆印象>", dialogMapStr);
        }

        String userDialogMapStr = memoryCapability.getUserDialogMapStr(userId);
        if (userDialogMapStr != null && !userDialogMapStr.isEmpty() && !cognationCapability.isSingleUser()) {
            map.put("[用户记忆缓存] <与最新一条消息的发送者的近两天对话记忆印象, 可能与[记忆缓存]稍有重复>", userDialogMapStr);
        }

        String sliceStr = memoryCapability.getActivatedSlicesStr(userId);
        if (sliceStr != null && !sliceStr.isEmpty()) {
            map.put("[记忆切片] <你与最新一条消息的发送者的相关回忆, 不会与[记忆缓存]重复, 如果有重复你也可以指出来()>", sliceStr);
        }
        return map;
    }

}
