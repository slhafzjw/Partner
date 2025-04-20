package work.slhaf.agent.modules.memory;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.memory.MemoryManager;
import work.slhaf.agent.core.memory.pojo.MemoryResult;

import java.io.IOException;
import java.time.LocalDate;

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
    public void execute(InteractionContext interactionContext) throws IOException, ClassNotFoundException {
        //获取主题路径
        JSONObject extractorResult = memorySelectExtractor.execute(interactionContext.getInput());
        String selectType = extractorResult.getString("type");
        //根据主结果进行操作查找切片
        MemoryResult memoryResult = switch (selectType) {
            case MemorySelectExtractor.Constant.DATE ->
                    memoryManager.selectMemory(LocalDate.parse(extractorResult.getString(MemorySelectExtractor.Constant.DATE)));
            case MemorySelectExtractor.Constant.TOPIC ->
                    memoryManager.selectMemory(MemorySelectExtractor.Constant.TOPIC);
            default -> null;
        };
        //评估切片
        if (memoryResult == null) {
            memoryResult = sliceEvaluator.execute(memoryResult,interactionContext);
        }

        //设置上下文

    }
}
