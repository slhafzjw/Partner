package work.slhaf.partner.module.modules.action.planner.evaluator;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.factory.component.annotation.Init;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.common.thread.InteractionThreadPoolExecutor;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.memory.pojo.EvaluatedSlice;
import work.slhaf.partner.module.modules.action.planner.evaluator.entity.EvaluatorBatchInput;
import work.slhaf.partner.module.modules.action.planner.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.modules.action.planner.evaluator.entity.EvaluatorResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class ActionEvaluator extends AbstractAgentModule.Sub<EvaluatorInput, List<EvaluatorResult>> implements ActivateModel {
    @InjectCapability
    private ActionCapability actionCapability;
    private InteractionThreadPoolExecutor executor;

    @Init
    public void init() {
        executor = InteractionThreadPoolExecutor.getInstance();
    }

    /**
     * 对输入的行为倾向进行评估，并根据评估结果，对缓存做出调整
     *
     * @param data 评估输入内容，包含提取/命中缓存的行动倾向、近几条聊天记录，正在生效的记忆切片内容
     * @return 评估结果集合
     */
    @Override
    public List<EvaluatorResult> execute(EvaluatorInput data) {
        List<EvaluatorBatchInput> batchInputs = buildEvaluatorBatchInput(data);
        List<Callable<EvaluatorResult>> tasks = getTasks(batchInputs);
        return executor.invokeAllAndReturn(tasks);
    }

    private List<Callable<EvaluatorResult>> getTasks(List<EvaluatorBatchInput> batchInputs) {
        List<Callable<EvaluatorResult>> list = new ArrayList<>();
        for (EvaluatorBatchInput batchInput : batchInputs) {
            list.add(() -> {
                ChatResponse response = this.singleChat(buildPrompt(batchInput));
                EvaluatorResult evaluatorResult = JSONObject.parseObject(response.getMessage(), EvaluatorResult.class);
                evaluatorResult.setTendency(batchInput.getTendency());
                return evaluatorResult;
            });
        }
        return list;
    }

    private List<EvaluatorBatchInput> buildEvaluatorBatchInput(EvaluatorInput data) {
        List<EvaluatorBatchInput> list = new ArrayList<>();
        for (String tendency : data.getTendencies()) {
            EvaluatorBatchInput temp = new EvaluatorBatchInput();
            BeanUtil.copyProperties(data, temp);
            temp.setTendency(tendency);
            Map<String, String> availableActions = new HashMap<>();
            actionCapability.listAvailableMetaActions().forEach((key, info) -> availableActions.put(key, info.getDescription()));
            temp.setAvailableActions(availableActions);
            list.add(temp);
        }
        return list;
    }

    private String buildPrompt(EvaluatorBatchInput batchInput) {
        JSONObject prompt = new JSONObject();
        prompt.put("[行动倾向]", batchInput.getTendency());
        JSONArray memoryData = prompt.putArray("[相关记忆切片]");
        for (EvaluatedSlice evaluatedSlice : batchInput.getActivatedSlices()) {
            JSONObject memory = memoryData.addObject();
            memory.put("[日期]", evaluatedSlice.getDate());
            memory.put("[摘要]", evaluatedSlice.getSummary());
        }
        JSONObject availableActionData = prompt.putObject("[可用行动单元]");
        availableActionData.putAll(batchInput.getAvailableActions());
        return prompt.toString();
    }

    @Override
    public String modelKey() {
        return "action_evaluator";
    }

    @Override
    public boolean withBasicPrompt() {
        return true;
    }
}
