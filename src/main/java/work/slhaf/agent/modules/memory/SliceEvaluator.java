package work.slhaf.agent.modules.memory;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;
import work.slhaf.agent.core.interaction.InteractionThreadPoolExecutor;
import work.slhaf.agent.core.memory.MemoryManager;
import work.slhaf.agent.core.memory.pojo.MemoryResult;
import work.slhaf.agent.core.memory.pojo.MemorySlice;
import work.slhaf.agent.core.memory.pojo.MemorySliceResult;
import work.slhaf.agent.modules.memory.data.evaluator.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class SliceEvaluator extends Model {
    public static final String MODEL_KEY = "slice_evaluator";

    private static SliceEvaluator sliceEvaluator;
    private MemoryManager memoryManager;
    private InteractionThreadPoolExecutor executor;

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

    public List<EvaluatedSlice> execute(EvaluatorInput evaluatorInput) throws InterruptedException {
        List<MemoryResult> memoryResultList = evaluatorInput.getMemoryResults();
        List<Callable<Void>> tasks = new ArrayList<>();
        Queue<EvaluatedSlice> queue = new ConcurrentLinkedDeque<>();
        for (MemoryResult memoryResult : memoryResultList) {
            tasks.add(() -> {
                List<SliceSummary> sliceSummaryList = new ArrayList<>();
                //映射查找键值
                Map<Long, SliceSummary> map = new HashMap<>();
                setSliceSummaryList(memoryResult, sliceSummaryList, map);
                try {
                    EvaluatorBatchInput batchInput = EvaluatorBatchInput.builder()
                            .text(evaluatorInput.getInput())
                            .memory_slices(sliceSummaryList)
                            .history(evaluatorInput.getMessages())
                            .build();
                    EvaluatorResult evaluatorResult = JSONObject.parseObject(singleChat(JSONUtil.toJsonStr(batchInput)).getMessage(), EvaluatorResult.class);
                    for (Long result : evaluatorResult.getResults()) {
                        SliceSummary sliceSummary = map.get(result);
                        EvaluatedSlice evaluatedSlice = EvaluatedSlice.builder()
                                .summary(sliceSummary.getSummary())
                                .date(sliceSummary.getDate())
                                .build();
                        queue.offer(evaluatedSlice);
                    }
                } catch (Exception e) {
                    log.error("切片评估: {}", e.getLocalizedMessage());
                }
                return null;
            });
        }

        executor.invokeAll(tasks, 30, TimeUnit.SECONDS);

        return queue.stream().toList();
    }

    private void setSliceSummaryList(MemoryResult memoryResult, List<SliceSummary> sliceSummaryList, Map<Long, SliceSummary> map) {
        for (MemorySliceResult memorySliceResult : memoryResult.getMemorySliceResult()) {

            SliceSummary sliceSummary = new SliceSummary();
            sliceSummary.setId(memorySliceResult.getMemorySlice().getTimestamp());
            String stringBuilder = memorySliceResult.getSliceBefore().getSummary() +
                    "\r\n" +
                    memorySliceResult.getMemorySlice().getSummary() +
                    "\r\n" +
                    memorySliceResult.getSliceAfter().getSummary();
            sliceSummary.setSummary(stringBuilder);
            Long timestamp = memorySliceResult.getMemorySlice().getTimestamp();
            sliceSummary.setDate(DateUtil.date(timestamp).toLocalDateTime().toLocalDate());

            sliceSummaryList.add(sliceSummary);
            map.put(timestamp, sliceSummary);
        }

        for (MemorySlice memorySlice : memoryResult.getRelatedMemorySliceResult()) {
            SliceSummary sliceSummary = new SliceSummary();
            sliceSummary.setId(memorySlice.getTimestamp());
            sliceSummary.setSummary(memorySlice.getSummary());

            sliceSummaryList.add(sliceSummary);
            map.put(memorySlice.getTimestamp(), sliceSummary);
        }
    }


}
