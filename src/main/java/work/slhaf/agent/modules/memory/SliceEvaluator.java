package work.slhaf.agent.modules.memory;

import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.memory.MemoryManager;
import work.slhaf.agent.core.memory.pojo.MemoryResult;
import work.slhaf.agent.core.memory.pojo.MemorySlice;
import work.slhaf.agent.core.memory.pojo.MemorySliceResult;
import work.slhaf.agent.modules.memory.data.SliceSummary;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class SliceEvaluator extends Model {
    public static final String MODEL_KEY = "slice_evaluator";

    private static SliceEvaluator sliceEvaluator;
    private MemoryManager memoryManager;

    private SliceEvaluator() {
    }

    public static SliceEvaluator getInstance() throws IOException, ClassNotFoundException {
        if (sliceEvaluator == null) {
            Config config = Config.getConfig();
            sliceEvaluator = new SliceEvaluator();
            sliceEvaluator.setMemoryManager(MemoryManager.getInstance());
            setModel(config, sliceEvaluator, MODEL_KEY, ModelConstant.SLICE_EVALUATOR_PROMPT);
            log.info("SliceEvaluator注册完毕...");
        }

        return sliceEvaluator;
    }

    public MemoryResult execute(MemoryResult memoryResult, InteractionContext context) {
        List<SliceSummary> sliceSummaryList = new ArrayList<>();
        setSliceSummaryList(memoryResult, context, sliceSummaryList);
        String primaryJsonStr = singleChat(JSONUtil.toJsonStr(sliceSummaryList)).getMessage();
        //TODO 解析并转换为过滤后的MemoryResult

        return null;
    }

    private void setSliceSummaryList(MemoryResult memoryResult, InteractionContext context, List<SliceSummary> sliceSummaryList) {
        for (MemorySliceResult memorySliceResult : memoryResult.getMemorySliceResult()) {
            //判断是否为发起用户
            if (accessible(memorySliceResult.getMemorySlice(), context)) {
                SliceSummary sliceSummary = new SliceSummary();
                sliceSummary.setId(memorySliceResult.getMemorySlice().getTimestamp());
                String stringBuilder = memorySliceResult.getSliceBefore().getSummary() +
                        "\r\n" +
                        memorySliceResult.getMemorySlice().getSummary() +
                        "\r\n" +
                        memorySliceResult.getSliceAfter().getSummary();
                sliceSummary.setSummary(stringBuilder);

                sliceSummaryList.add(sliceSummary);
            }
        }

        for (MemorySlice memorySlice : memoryResult.getRelatedMemorySliceResult()) {
            SliceSummary sliceSummary = new SliceSummary();
            sliceSummary.setId(memorySlice.getTimestamp());
            sliceSummary.setSummary(memorySlice.getSummary());

            sliceSummaryList.add(sliceSummary);
        }
    }


    private boolean accessible(MemorySlice slice, InteractionContext context) {
        boolean ok;
        String startUserId = slice.getStartUserId();
        String userInfo = context.getUserInfo();
        String nickName = context.getUserNickname();

        if (memoryManager.getUserId(userInfo, nickName).equals(startUserId)) {
            ok = true;
        } else {
            ok = !slice.isPrivate();
        }

        return ok;
    }

}
