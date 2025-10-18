package work.slhaf.partner.module.modules.action.planner.evaluator;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson2.JSONObject;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.common.thread.InteractionThreadPoolExecutor;
import work.slhaf.partner.module.modules.action.planner.evaluator.entity.EvaluatorBatchInput;
import work.slhaf.partner.module.modules.action.planner.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.modules.action.planner.evaluator.entity.EvaluatorResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@AgentSubModule
public class ActionEvaluator extends AgentRunningSubModule<EvaluatorInput, List<EvaluatorResult>> implements ActivateModel {

    private InteractionThreadPoolExecutor executor;

    @Init
    public void init() {
        executor = InteractionThreadPoolExecutor.getInstance();
    }

    /**
     * 对输入的行为倾向进行评估，并根据评估结果，对缓存做出调整
     *
     * @param data 评估输入内容，包含提取/命中缓存的行动倾向、近几条聊天记录，正在生效的记忆切片内容
     * @return 评估结果集合，包含
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
                ChatResponse response = this.singleChat(JSONObject.toJSONString(batchInput));
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
            list.add(temp);
        }
        return list;
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
