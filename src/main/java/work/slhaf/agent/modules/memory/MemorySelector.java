package work.slhaf.agent.modules.memory;

import lombok.Data;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.memory.MemoryManager;
import work.slhaf.agent.core.memory.pojo.MemoryResult;
import work.slhaf.agent.core.memory.pojo.MemorySlice;
import work.slhaf.agent.modules.memory.data.evaluator.EvaluatedSlice;
import work.slhaf.agent.modules.memory.data.evaluator.EvaluatorInput;
import work.slhaf.agent.modules.memory.data.evaluator.EvaluatorResult;
import work.slhaf.agent.modules.memory.data.extractor.ExtractorMatchData;
import work.slhaf.agent.modules.memory.data.extractor.ExtractorResult;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class MemorySelector implements InteractionModule {

    private static MemorySelector memorySelector;

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
        //获取主题路径
        ExtractorResult extractorResult = memorySelectExtractor.execute(interactionContext);
        if (extractorResult.isRecall()) {
            //查找切片
            List<MemoryResult> memoryResultList = new ArrayList<>();
            setMemoryResultList(memoryResultList, extractorResult.getMatches(), interactionContext.getUserInfo(), interactionContext.getUserNickname());
            //评估切片
            EvaluatorInput evaluatorInput = EvaluatorInput.builder()
                    .input(interactionContext.getInput())
                    .memoryResults(memoryResultList)
                    .messages(memoryManager.getChatMessages())
                    .build();
            List<EvaluatedSlice> memorySlices = sliceEvaluator.execute(evaluatorInput);
            //设置上下文
            interactionContext.getModuleContext().put("memory_slices",memorySlices);
        }

    }

    private void setMemoryResultList(List<MemoryResult> memoryResultList, List<ExtractorMatchData> matches, String userInfo, String nickName) throws IOException, ClassNotFoundException {
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
            memoryResult.getMemorySliceResult().removeIf(m -> removeOrNot(m.getMemorySlice(), userInfo, nickName));
            //过滤邻近记忆
            memoryResult.getRelatedMemorySliceResult().removeIf(m -> removeOrNot(m, userInfo, nickName));
        }
    }

    private boolean removeOrNot(MemorySlice memorySlice, String userInfo, String nickName) {
        if (memorySlice.isPrivate()) {
            String userId = memoryManager.getUserId(userInfo, nickName);
            return memorySlice.getStartUserId().equals(userId);
        }
        return true;
    }
}
