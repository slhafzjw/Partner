package work.slhaf.partner.module.memory.selector.evaluator;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.factory.component.annotation.Init;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.common.thread.InteractionThreadPoolExecutor;
import work.slhaf.partner.core.memory.pojo.ActivatedMemorySlice;
import work.slhaf.partner.module.memory.selector.evaluator.entity.EvaluatorBatchInput;
import work.slhaf.partner.module.memory.selector.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.memory.selector.evaluator.entity.EvaluatorResult;
import work.slhaf.partner.module.memory.selector.evaluator.entity.SliceSummary;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@EqualsAndHashCode(callSuper = true)
@Data
public class SliceSelectEvaluator extends AbstractAgentModule.Sub<EvaluatorInput, List<ActivatedMemorySlice>> implements ActivateModel {
    private InteractionThreadPoolExecutor executor;

    @Init
    public void init() {
        executor = InteractionThreadPoolExecutor.getInstance();
    }

    @Override
    public List<ActivatedMemorySlice> execute(EvaluatorInput evaluatorInput) {
        log.debug("[SliceSelectEvaluator] 切片评估模块开始...");
        List<ActivatedMemorySlice> memorySlices = evaluatorInput.getMemorySlices();
        List<Callable<Void>> tasks = new ArrayList<>();
        Queue<ActivatedMemorySlice> queue = new ConcurrentLinkedDeque<>();
        AtomicInteger count = new AtomicInteger(0);
        if (memorySlices == null || memorySlices.isEmpty()) {
            return List.of();
        }
        tasks.add(() -> {
            int thisCount = count.incrementAndGet();
            log.debug("[SliceSelectEvaluator] 评估[{}]开始", thisCount);
            List<SliceSummary> sliceSummaryList = new ArrayList<>();
            Map<Long, ActivatedMemorySlice> map = new HashMap<>();
            try {
                setSliceSummaryList(memorySlices, sliceSummaryList, map);
                EvaluatorBatchInput batchInput = EvaluatorBatchInput.builder()
                        .text(evaluatorInput.getInput())
                        .memory_slices(sliceSummaryList)
                        .history(evaluatorInput.getMessages())
                        .build();
                log.debug("[SliceSelectEvaluator] 评估[{}]输入: {}", thisCount, JSONObject.toJSONString(batchInput));
                EvaluatorResult evaluatorResult = formattedChat(
                        List.of(new Message(Message.Character.USER, JSONUtil.toJsonStr(batchInput))),
                        EvaluatorResult.class
                );
                log.debug("[SliceSelectEvaluator] 评估[{}]结果: {}", thisCount, JSONObject.toJSONString(evaluatorResult));
                for (Long result : evaluatorResult.getResults()) {
                    ActivatedMemorySlice slice = map.get(result);
                    if (slice != null) {
                        queue.offer(slice);
                    }
                }
            } catch (Exception e) {
                log.error("[SliceSelectEvaluator] 评估[{}]出现错误: {}", thisCount, e.getLocalizedMessage());
            }
            return null;
        });
        executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
        log.debug("[SliceSelectEvaluator] 评估模块结束, 输出队列: {}", queue);
        return new ArrayList<>(queue);
    }

    private void setSliceSummaryList(List<ActivatedMemorySlice> memorySlices, List<SliceSummary> sliceSummaryList,
                                     Map<Long, ActivatedMemorySlice> map) {
        for (ActivatedMemorySlice memorySlice : memorySlices) {
            SliceSummary sliceSummary = new SliceSummary();
            sliceSummary.setId(memorySlice.getTimestamp());
            sliceSummary.setSummary(memorySlice.getSummary());
            sliceSummary.setDate(memorySlice.getDate());
            sliceSummaryList.add(sliceSummary);
            map.put(memorySlice.getTimestamp(), memorySlice);
        }
    }

    public String modelKey() {
        return "slice_evaluator";
    }
}
