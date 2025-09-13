package work.slhaf.partner.module.modules.memory.selector.evaluator;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.common.thread.InteractionThreadPoolExecutor;
import work.slhaf.partner.core.common.pojo.MemoryResult;
import work.slhaf.partner.core.common.pojo.MemorySliceResult;
import work.slhaf.partner.core.submodule.memory.pojo.EvaluatedSlice;
import work.slhaf.partner.core.submodule.memory.pojo.MemorySlice;
import work.slhaf.partner.module.modules.memory.selector.evaluator.data.EvaluatorBatchInput;
import work.slhaf.partner.module.modules.memory.selector.evaluator.data.EvaluatorInput;
import work.slhaf.partner.module.modules.memory.selector.evaluator.data.EvaluatorResult;
import work.slhaf.partner.module.modules.memory.selector.evaluator.data.SliceSummary;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static work.slhaf.partner.common.util.ExtractUtil.extractJson;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
@AgentSubModule
public class SliceSelectEvaluator extends AgentRunningSubModule<EvaluatorInput, List<EvaluatedSlice>> implements ActivateModel {

    private InteractionThreadPoolExecutor executor;

    @Init
    public void init() {
        executor = InteractionThreadPoolExecutor.getInstance();
    }

    @Override
    public List<EvaluatedSlice> execute(EvaluatorInput evaluatorInput) {
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
                    log.debug("[SliceSelectEvaluator] 评估[{}]输入: {}", thisCount, JSONObject.toJSONString(batchInput));
                    EvaluatorResult evaluatorResult = JSONObject.parseObject(extractJson(singleChat(JSONUtil.toJsonStr(batchInput)).getMessage()), EvaluatorResult.class);
                    log.debug("[SliceSelectEvaluator] 评估[{}]结果: {}", thisCount, JSONObject.toJSONString(evaluatorResult));
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
        List<EvaluatedSlice> temp = queue.stream().toList();
        return new  ArrayList<>(temp);
    }

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


    public String modelKey() {
        return "slice_evaluator";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }

}
