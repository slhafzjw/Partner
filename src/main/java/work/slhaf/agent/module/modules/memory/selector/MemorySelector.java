package work.slhaf.agent.module.modules.memory.selector;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.exception_handler.GlobalExceptionHandler;
import work.slhaf.agent.common.exception_handler.pojo.GlobalException;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.memory.MemoryManager;
import work.slhaf.agent.core.memory.exception.UnExistedDateIndexException;
import work.slhaf.agent.core.memory.exception.UnExistedTopicException;
import work.slhaf.agent.core.memory.pojo.MemoryResult;
import work.slhaf.agent.core.memory.pojo.MemorySlice;
import work.slhaf.agent.module.common.AppendPrompt;
import work.slhaf.agent.module.common.AppendPromptData;
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
public class MemorySelector implements InteractionModule, AppendPrompt {

    private static volatile MemorySelector memorySelector;
/*    public static final String appendPrompt = """
            新增输入字段示例:
            
            "memory_slices": [{ //记忆切片，可能为多个
                "chatMessages": [{
                    "role": "user"/"assistant", //该信息发送者
                    "content": "消息内容"
                }],
                "date": "2024-03-20", //切片日期
                "summary": "切片总结"
            }],
            "static_memory": "对于该用户的常识性记忆，如爱好、住处、生日",
            "dialog_map": { //近两日的与所有用户的对话缓存
                "2023-01-01T11:30": "用户a[dawgbi-dwa-ccc] 尝试分享生活点滴并营造氛围感",
                "2023-01-02T11:30": "用户b[dawgbi-dwa-ccc] 尝试分享生活点滴并营造氛围感"
            }
            "user_dialog_map": { //与当前用户的近两日对话缓存
                "2023-01-01T11:30": "与用户讨论了...",
                "2023-01-02T11:30": "与用户讨论了..."
            }
            
            无新增输出字段
            
            ##注意
              a. 这些字段中可能出现的第一人称描述都是指`Partner`，即你所属的智能体，当前用户正在对话的对象
              b. `dialog_map`和`user_dialog_map`中，值都将以`用户昵称[用户uuid]`开头，你需要正确区分不同用户
              c. 若`text`字段，即用户的真正输入内容未涉及`dialog_map`, `user_dialog_map`等字段中的内容，你需要仅根据用户的输入来确定如何回复.当用户未提及时，这两个字段中的内容时，你不需要主动提起.
              d. 做出回应时，你需要考虑上述新增字段与当前的时间差异
            """;*/

    private MemoryManager memoryManager;
    private SliceSelectEvaluator sliceSelectEvaluator;
    private MemorySelectExtractor memorySelectExtractor;

    private MemorySelector() {
    }

    public static MemorySelector getInstance() throws IOException, ClassNotFoundException {
        if (memorySelector == null) {
            synchronized (MemorySelector.class) {
                if (memorySelector == null) {
                    memorySelector = new MemorySelector();
                    memorySelector.setMemoryManager(MemoryManager.getInstance());
                    memorySelector.setSliceSelectEvaluator(SliceSelectEvaluator.getInstance());
                    memorySelector.setMemorySelectExtractor(MemorySelectExtractor.getInstance());
                }
            }
        }
        return memorySelector;
    }

    @Override
    public void execute(InteractionContext interactionContext) throws IOException, ClassNotFoundException, InterruptedException {
        log.debug("[MemorySelector] 记忆回溯流程开始...");
        String userId = interactionContext.getUserId();
        //获取主题路径
        ExtractorResult extractorResult = memorySelectExtractor.execute(interactionContext);
        if (extractorResult.isRecall() || !extractorResult.getMatches().isEmpty()) {
            memoryManager.getActivatedSlices().get(userId).clear();
            List<EvaluatedSlice> evaluatedSlices = selectAndEvaluateMemory(interactionContext, extractorResult);
            memoryManager.updateActivatedSlices(userId, evaluatedSlices);
        }
        //设置上下文
//        setCoreContext(interactionContext);
        //设置追加提示词
        setAppendedPrompt(interactionContext);
        setModuleContextRecall(interactionContext);
        log.debug("[MemorySelector] 记忆回溯结果: {}", interactionContext);
    }

    private List<EvaluatedSlice> selectAndEvaluateMemory(InteractionContext interactionContext, ExtractorResult extractorResult) throws IOException, ClassNotFoundException, InterruptedException {
        log.debug("[MemorySelector] 触发记忆回溯...");
        //查找切片
        String userId = interactionContext.getUserId();
        List<MemoryResult> memoryResultList = new ArrayList<>();
        setMemoryResultList(memoryResultList, extractorResult.getMatches(), userId);
        //评估切片
        EvaluatorInput evaluatorInput = EvaluatorInput.builder()
                .input(interactionContext.getInput())
                .memoryResults(memoryResultList)
                .messages(memoryManager.getChatMessages())
                .build();
        log.debug("[MemorySelector] 切片评估输入: {}", evaluatorInput);
        List<EvaluatedSlice> memorySlices = sliceSelectEvaluator.execute(evaluatorInput);
        log.debug("[MemorySelector] 切片评估结果: {}", memorySlices);
        return memorySlices;
    }

    /*private void setCoreContext(InteractionContext interactionContext) {
        String userId = interactionContext.getUserId();
        interactionContext.getCoreContext().put("memory_slices", memoryManager.getActivatedSlices().get(userId));
//        interactionContext.getCoreContext().put("static_memory", memoryManager.getStaticMemory(userId));
        interactionContext.getCoreContext().put("dialog_map", memoryManager.getDialogMap());
        interactionContext.getCoreContext().put("user_dialog_map", memoryManager.getUserDialogMap(userId));
    }*/

    private void setModuleContextRecall(InteractionContext interactionContext) {
        String userId = interactionContext.getUserId();
        boolean recall;
        if (memoryManager.getActivatedSlices().get(userId) == null) {
            recall = false;
        } else {
            recall = !memoryManager.getActivatedSlices().get(userId).isEmpty();
        }
        interactionContext.getModuleContext().put("recall", recall);
        if (recall) {
            interactionContext.getModuleContext().put("recall_count", memoryManager.getActivatedSlices().get(userId).size());
        }
    }


    private void setMemoryResultList(List<MemoryResult> memoryResultList, List<ExtractorMatchData> matches, String userId) throws IOException, ClassNotFoundException {
        for (ExtractorMatchData match : matches) {
            try {
                MemoryResult memoryResult = switch (match.getType()) {
                    case ExtractorMatchData.Constant.TOPIC -> memoryManager.selectMemory(match.getText());
                    case ExtractorMatchData.Constant.DATE ->
                            memoryManager.selectMemory(LocalDate.parse(match.getText()));
                    default -> null;
                };
                if (memoryResult == null) continue;
                removeDuplicateSlice(memoryResult);
                memoryResultList.add(memoryResult);
            } catch (UnExistedDateIndexException | UnExistedTopicException e) {
                log.error("[MemorySelector] 不存在的记忆索引! 请尝试更换更合适的主题提取LLM!", e);
                GlobalExceptionHandler.writeExceptionState(new GlobalException(e.getMessage()));
            }
        }
        //清理切片记录
        memoryManager.cleanSelectedSliceFilter();

        //根据userInfo过滤是否为私人记忆
        for (MemoryResult memoryResult : memoryResultList) {
            //过滤终点记忆
            memoryResult.getMemorySliceResult().removeIf(m -> removeOrNot(m.getMemorySlice(), userId));
            //过滤邻近记忆
            memoryResult.getRelatedMemorySliceResult().removeIf(m -> removeOrNot(m, userId));
        }
    }

    private void removeDuplicateSlice(MemoryResult memoryResult) {
        Collection<String> values = memoryManager.getDialogMap().values();
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
        data.setComment("[记忆模块]");
        data.setAppendedPrompt(map);
        context.setAppendedPrompt(data);
    }

    private HashMap<String, String> getPromptDataMap(String userId) {
        HashMap<String, String> map = new HashMap<>();
        String dialogMapStr = memoryManager.getDialogMapStr();
        if (!dialogMapStr.isEmpty()) {
            map.put("[记忆缓存] <你最近两日和所有聊天者的对话记忆印象>", dialogMapStr);
        }

        String userDialogMapStr = memoryManager.getUserDialogMapStr(userId);
        if (!userDialogMapStr.isEmpty()) {
            map.put("[用户记忆缓存] <与最新一条消息的发送者的近两天对话记忆印象, 可能与[记忆缓存]稍有重复>", "与当前用户的近两日对话缓存");
        }

        String sliceStr = memoryManager.getActivatedSlicesStr(userId);
        if (!sliceStr.isEmpty()){
            map.put("[记忆切片] <你与最新一条消息的发送者的相关回忆, 不会与[记忆缓存]重复, 如果有重复你也可以指出来()>", sliceStr);
        }
        return map;
    }
}
