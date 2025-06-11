package work.slhaf.agent.module.modules.memory.selector;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.core.cognation.CognationCapability;
import work.slhaf.agent.core.cognation.CognationManager;
import work.slhaf.agent.core.cognation.common.exception.UnExistedDateIndexException;
import work.slhaf.agent.core.cognation.common.exception.UnExistedTopicException;
import work.slhaf.agent.core.cognation.common.pojo.MemoryResult;
import work.slhaf.agent.core.cognation.submodule.cache.CacheCapability;
import work.slhaf.agent.core.cognation.submodule.memory.MemoryCapability;
import work.slhaf.agent.core.cognation.submodule.memory.pojo.MemorySlice;
import work.slhaf.agent.core.interaction.data.context.InteractionContext;
import work.slhaf.agent.core.interaction.module.InteractionModule;
import work.slhaf.agent.core.session.SessionManager;
import work.slhaf.agent.module.common.AppendPromptData;
import work.slhaf.agent.module.common.PreModuleActions;
import work.slhaf.agent.module.modules.memory.selector.evaluator.SliceSelectEvaluator;
import work.slhaf.agent.module.modules.memory.selector.evaluator.data.EvaluatorInput;
import work.slhaf.agent.module.modules.memory.selector.extractor.MemorySelectExtractor;
import work.slhaf.agent.module.modules.memory.selector.extractor.data.ExtractorMatchData;
import work.slhaf.agent.module.modules.memory.selector.extractor.data.ExtractorResult;
import work.slhaf.agent.shared.memory.EvaluatedSlice;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

@Data
@Slf4j
public class MemorySelector implements InteractionModule, PreModuleActions {

    private static volatile MemorySelector memorySelector;

    private CacheCapability cacheCapability;
    private MemoryCapability memoryCapability;
    private CognationCapability cognationCapability;
    private SliceSelectEvaluator sliceSelectEvaluator;
    private MemorySelectExtractor memorySelectExtractor;
    private SessionManager sessionManager;

    private MemorySelector() {
    }

    public static MemorySelector getInstance() throws IOException, ClassNotFoundException {
        if (memorySelector == null) {
            synchronized (MemorySelector.class) {
                if (memorySelector == null) {
                    memorySelector = new MemorySelector();
                    memorySelector.setCacheCapability(CognationManager.getInstance());
                    memorySelector.setMemoryCapability(CognationManager.getInstance());
                    memorySelector.setCognationCapability(CognationManager.getInstance());
                    memorySelector.setSliceSelectEvaluator(SliceSelectEvaluator.getInstance());
                    memorySelector.setMemorySelectExtractor(MemorySelectExtractor.getInstance());
                    memorySelector.setSessionManager(SessionManager.getInstance());
                }
            }
        }
        return memorySelector;
    }

    @Override
    public void execute(InteractionContext interactionContext) throws IOException, ClassNotFoundException {
        log.debug("[MemorySelector] 记忆回溯流程开始...");
        String userId = interactionContext.getUserId();
        //获取主题路径
        ExtractorResult extractorResult = memorySelectExtractor.execute(interactionContext);
        if (extractorResult.isRecall() || !extractorResult.getMatches().isEmpty()) {
            cognationCapability.clearActivatedSlices(userId);
            List<EvaluatedSlice> evaluatedSlices = selectAndEvaluateMemory(interactionContext, extractorResult);
            cognationCapability.updateActivatedSlices(userId, evaluatedSlices);
        }
        //设置追加提示词
        setAppendedPrompt(interactionContext);
        setModuleContextRecall(interactionContext);
        setActiveModule(interactionContext);
        log.debug("[MemorySelector] 记忆回溯完成...");
    }

    private List<EvaluatedSlice> selectAndEvaluateMemory(InteractionContext interactionContext, ExtractorResult extractorResult) throws IOException, ClassNotFoundException {
        log.debug("[MemorySelector] 触发记忆回溯...");
        //查找切片
        String userId = interactionContext.getUserId();
        List<MemoryResult> memoryResultList = new ArrayList<>();
        setMemoryResultList(memoryResultList, extractorResult.getMatches(), userId);
        //评估切片
        EvaluatorInput evaluatorInput = EvaluatorInput.builder()
                .input(interactionContext.getInput())
                .memoryResults(memoryResultList)
                .messages(cognationCapability.getChatMessages())
                .build();
        log.debug("[MemorySelector] 切片评估输入: {}", JSONObject.toJSONString(evaluatorInput));
        List<EvaluatedSlice> memorySlices = sliceSelectEvaluator.execute(evaluatorInput);
        log.debug("[MemorySelector] 切片评估结果: {}", JSONObject.toJSONString(memorySlices));
        return memorySlices;
    }

    private void setModuleContextRecall(InteractionContext interactionContext) {
        String userId = interactionContext.getUserId();
        boolean recall = cognationCapability.hasActivatedSlices(userId);
        interactionContext.getModuleContext().getExtraContext().put("recall", recall);
        if (recall) {
            interactionContext.getModuleContext().getExtraContext().put("recall_count", cognationCapability.getActivatedSlicesSize(userId));
        }
    }


    private void setMemoryResultList(List<MemoryResult> memoryResultList, List<ExtractorMatchData> matches, String userId) throws IOException, ClassNotFoundException {
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
        Collection<String> values = cacheCapability.getDialogMap().values();
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
    public void setAppendedPrompt(InteractionContext context) {
        String userId = context.getUserId();
        HashMap<String, String> map = getPromptDataMap(userId);
        AppendPromptData data = new AppendPromptData();
        data.setModuleName(getModuleName());
        data.setAppendedPrompt(map);
        context.setAppendedPrompt(data);
    }

    @Override
    public void setActiveModule(InteractionContext context) {
        context.getCoreContext().addActiveModule(getModuleName());
    }

    @Override
    public String getModuleName() {
        return "[记忆模块]";
    }

    private HashMap<String, String> getPromptDataMap(String userId) {
        HashMap<String, String> map = new HashMap<>();
        String dialogMapStr = cacheCapability.getDialogMapStr();
        if (!dialogMapStr.isEmpty()) {
            map.put("[记忆缓存] <你最近两日和所有聊天者的对话记忆印象>", dialogMapStr);
        }

        String userDialogMapStr = cacheCapability.getUserDialogMapStr(userId);
        if (userDialogMapStr != null && !userDialogMapStr.isEmpty() && !cognationCapability.isSingleUser()) {
            map.put("[用户记忆缓存] <与最新一条消息的发送者的近两天对话记忆印象, 可能与[记忆缓存]稍有重复>", userDialogMapStr);
        }

        String sliceStr = cognationCapability.getActivatedSlicesStr(userId);
        if (sliceStr != null && !sliceStr.isEmpty()) {
            map.put("[记忆切片] <你与最新一条消息的发送者的相关回忆, 不会与[记忆缓存]重复, 如果有重复你也可以指出来()>", sliceStr);
        }
        return map;
    }

}
