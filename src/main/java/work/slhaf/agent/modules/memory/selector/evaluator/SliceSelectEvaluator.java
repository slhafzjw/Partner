package work.slhaf.agent.modules.memory.selector.evaluator;

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
import work.slhaf.agent.modules.memory.selector.evaluator.data.EvaluatorBatchInput;
import work.slhaf.agent.modules.memory.selector.evaluator.data.EvaluatorInput;
import work.slhaf.agent.modules.memory.selector.evaluator.data.EvaluatorResult;
import work.slhaf.agent.modules.memory.selector.evaluator.data.SliceSummary;
import work.slhaf.agent.shared.memory.EvaluatedSlice;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static work.slhaf.agent.common.util.ExtractUtil.extractJson;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class SliceSelectEvaluator extends Model {
    public static final String MODEL_KEY = "slice_evaluator";
    private static SliceSelectEvaluator sliceSelectEvaluator;
    private MemoryManager memoryManager;
    private InteractionThreadPoolExecutor executor;

    private SliceSelectEvaluator() {
    }

    public static SliceSelectEvaluator getInstance() throws IOException, ClassNotFoundException {
        if (sliceSelectEvaluator == null) {
            sliceSelectEvaluator = new SliceSelectEvaluator();
            sliceSelectEvaluator.setMemoryManager(MemoryManager.getInstance());
            sliceSelectEvaluator.setExecutor(InteractionThreadPoolExecutor.getInstance());
            setModel(sliceSelectEvaluator, MODEL_KEY, ModelConstant.Prompt.MEMORY,false);
            log.info("SliceEvaluator注册完毕...");
        }

        return sliceSelectEvaluator;
    }

    public List<EvaluatedSlice> execute(EvaluatorInput evaluatorInput) throws InterruptedException {
        log.debug("[SliceSelectEvaluator] 切片评估模块开始...");
        List<MemoryResult> memoryResultList = evaluatorInput.getMemoryResults();
        List<Callable<Void>> tasks = new ArrayList<>();
        Queue<EvaluatedSlice> queue = new ConcurrentLinkedDeque<>();
        AtomicInteger count = new AtomicInteger(0);
        for (MemoryResult memoryResult : memoryResultList) {
            if (memoryResult.getMemorySliceResult().isEmpty() && memoryResult.getRelatedMemorySliceResult().isEmpty()) {
                continue;
            }
            tasks.add(() -> {
                int thisCount = count.incrementAndGet();
                log.debug("[SliceSelectEvaluator] 评估[{}]开始", thisCount);
                List<SliceSummary> sliceSummaryList = new ArrayList<>();
                //映射查找键值
                Map<Long, SliceSummary> map = new HashMap<>();
                try {
                    setSliceSummaryList(memoryResult, sliceSummaryList, map);
                    EvaluatorBatchInput batchInput = EvaluatorBatchInput.builder()
                            .text(evaluatorInput.getInput())
                            .memory_slices(sliceSummaryList)
                            .history(evaluatorInput.getMessages())
                            .build();
                    log.debug("[SliceSelectEvaluator] 评估[{}]输入: {}", thisCount, batchInput);
                    EvaluatorResult evaluatorResult = JSONObject.parseObject(extractJson(singleChat(JSONUtil.toJsonStr(batchInput)).getMessage()), EvaluatorResult.class);
                    log.debug("[SliceSelectEvaluator] 评估[{}]结果: {}", thisCount, evaluatorResult);
                    for (Long result : evaluatorResult.getResults()) {
                        SliceSummary sliceSummary = map.get(result);
                        EvaluatedSlice evaluatedSlice = EvaluatedSlice.builder()
                                .summary(sliceSummary.getSummary())
                                .date(sliceSummary.getDate())
                                .build();
//                        setEvaluatedSliceMessages(evaluatedSlice, memoryResult, sliceSummary.getId());
                        queue.offer(evaluatedSlice);
                    }
                } catch (Exception e) {
                    log.error("[SliceSelectEvaluator] 评估[{}]出现错误: {}", thisCount, e.getLocalizedMessage());
                }
                return null;
            });
        }

        executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
        log.debug("[SliceSelectEvaluator] 评估模块结束, 输出队列: {}", queue);
        return queue.stream().toList();
    }

/*    private void setEvaluatedSliceMessages(EvaluatedSlice evaluatedSlice, MemoryResult memoryResult, Long id) {
        //补充消息列表
        for (MemorySliceResult memorySliceResult : memoryResult.getMemorySliceResult()) {
            if (memorySliceResult.getMemorySlice().getTimestamp().equals(id)) {
                evaluatedSlice.setChatMessages(memorySliceResult.getMemorySlice().getChatMessages());
                return;
            }
        }
        for (MemorySlice memorySlice : memoryResult.getRelatedMemorySliceResult()) {
            if (memorySlice.getTimestamp().equals(id)) {
                evaluatedSlice.setChatMessages(memorySlice.getChatMessages());
                return;
            }
        }
    }*/

    private void setSliceSummaryList(MemoryResult memoryResult, List<SliceSummary> sliceSummaryList, Map<Long, SliceSummary> map) {
        for (MemorySliceResult memorySliceResult : memoryResult.getMemorySliceResult()) {

            SliceSummary sliceSummary = new SliceSummary();
            sliceSummary.setId(memorySliceResult.getMemorySlice().getTimestamp());
            StringBuilder stringBuilder = new StringBuilder();
            if (memorySliceResult.getSliceBefore() != null) {
                stringBuilder.append(memorySliceResult.getSliceBefore().getSummary())
                        .append("\r\n");
            }
            stringBuilder.append(memorySliceResult.getMemorySlice().getSummary());
            if (memorySliceResult.getSliceAfter() != null) {
                stringBuilder.append("\r\n")
                        .append(memorySliceResult.getSliceAfter().getSummary())
                        .append("\r\n");
            }
            sliceSummary.setSummary(stringBuilder.toString());
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
