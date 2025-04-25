package work.slhaf.agent.modules.memory;

import lombok.Data;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.memory.MemoryManager;
import work.slhaf.agent.core.memory.pojo.MemoryResult;
import work.slhaf.agent.core.memory.pojo.MemorySlice;
import work.slhaf.agent.modules.memory.data.evaluator.EvaluatorInput;
import work.slhaf.agent.modules.memory.data.extractor.ExtractorMatchData;
import work.slhaf.agent.modules.memory.data.extractor.ExtractorResult;
import work.slhaf.agent.shared.memory.EvaluatedSlice;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class MemorySelector implements InteractionModule {

    private static MemorySelector memorySelector;
    public static final String modulePrompt = """
            新增输入字段:
            
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
                "2023-01-01T11:30": "发生了...与用户A...、用户B谈到...",
                "2023-01-02T11:30": "发生了...与用户A...、用户B谈到..."
            }
            "user_dialog_map": { //与当前用户的近两日对话缓存
                "2023-01-01T11:30": "与用户讨论了...",
                "2023-01-02T11:30": "与用户讨论了..."
            }
            
            无新增输出字段
            """;

    private MemoryManager memoryManager;
    private SliceEvaluator sliceEvaluator;
    private MemorySelectExtractor memorySelectExtractor;

    private MemorySelector() {
    }

    public static MemorySelector getInstance() throws IOException, ClassNotFoundException {
        if (memorySelector == null) {
            memorySelector = new MemorySelector();
            memorySelector.setMemoryManager(MemoryManager.getInstance());
            memorySelector.setSliceEvaluator(SliceEvaluator.getInstance());
            memorySelector.setMemorySelectExtractor(MemorySelectExtractor.getInstance());
        }
        return memorySelector;
    }

    @Override
    public void execute(InteractionContext interactionContext) throws IOException, ClassNotFoundException, InterruptedException {
        String userId = memoryManager.getUserId(interactionContext.getUserInfo(), interactionContext.getUserNickname());
        //获取主题路径
        ExtractorResult extractorResult = memorySelectExtractor.execute(interactionContext);
        if (extractorResult.isRecall()) {
            //查找切片
            List<MemoryResult> memoryResultList = new ArrayList<>();
            setMemoryResultList(memoryResultList, extractorResult.getMatches(),userId);
            //评估切片
            EvaluatorInput evaluatorInput = EvaluatorInput.builder()
                    .input(interactionContext.getInput())
                    .memoryResults(memoryResultList)
                    .messages(memoryManager.getChatMessages())
                    .build();
            List<EvaluatedSlice> memorySlices = sliceEvaluator.execute(evaluatorInput);
            memoryManager.getActivatedSlices().put(userId,memorySlices);
        }

        //设置上下文
        interactionContext.getModuleContext().put("memory_slices",memoryManager.getActivatedSlices().get(userId));
        interactionContext.getModuleContext().put("static_memory",memoryManager.getStaticMemory(userId));
        interactionContext.getModuleContext().put("dialog_map",memoryManager.getDialogMap());
        interactionContext.getModuleContext().put("user_dialog_map",memoryManager.getUserDialogMap(userId));

        interactionContext.getModulePrompt().put("memory", modulePrompt);
    }

    private void setMemoryResultList(List<MemoryResult> memoryResultList, List<ExtractorMatchData> matches, String userId) throws IOException, ClassNotFoundException {
        for (ExtractorMatchData match : matches) {
            MemoryResult memoryResult = switch (match.getType()) {
                case ExtractorMatchData.Constant.DATE -> memoryManager.selectMemory(match.getText());
                case ExtractorMatchData.Constant.TOPIC -> memoryManager.selectMemory(LocalDate.parse(match.getText()));
                default -> null;
            };
            if (memoryResult == null) continue;
            memoryResultList.add(memoryResult);
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

    private boolean removeOrNot(MemorySlice memorySlice, String userId) {
        if (memorySlice.isPrivate()) {
            return memorySlice.getStartUserId().equals(userId);
        }
        return true;
    }
}
