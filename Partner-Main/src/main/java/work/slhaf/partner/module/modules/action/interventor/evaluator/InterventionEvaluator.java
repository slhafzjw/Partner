package work.slhaf.partner.module.modules.action.interventor.evaluator;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore.ExecutorType;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.core.action.entity.PhaserRecord;
import work.slhaf.partner.core.memory.pojo.EvaluatedSlice;
import work.slhaf.partner.module.modules.action.interventor.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.modules.action.interventor.evaluator.entity.EvaluatorResult;
import work.slhaf.partner.module.modules.action.interventor.evaluator.entity.EvaluatorResult.EvaluatedInterventionData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

@Slf4j
@AgentSubModule
public class InterventionEvaluator extends AgentRunningSubModule<EvaluatorInput, EvaluatorResult>
        implements ActivateModel {

    @InjectCapability
    private ActionCapability actionCapability;

    /**
     * 基于干预意图、记忆切片、交互上下文、已有行动程序综合评估，尝试评估并选取出合适的行动程序，交付给 ActionInterventor
     */
    @Override
    public EvaluatorResult execute(EvaluatorInput input) {
        // 获取必须数据
        ExecutorService executor = actionCapability.getExecutor(ExecutorType.VIRTUAL);
        Map<String, PhaserRecord> executingInterventions = input.getExecutingInterventions();
        Map<String, ActionData> preparedInterventions = input.getPreparedInterventions();
        CountDownLatch latch = new CountDownLatch(executingInterventions.size() + preparedInterventions.size());

        // 创建结果容器
        EvaluatorResult result = new EvaluatorResult();
        List<EvaluatedInterventionData> executingDataList = result.getExecutingDataList();
        List<EvaluatedInterventionData> preparedDataList = result.getPreparedDataList();

        // 并发评估
        evaluateIntervention(executingDataList, executingInterventions, input, executor, latch);
        evaluateIntervention(preparedDataList, preparedInterventions, input, executor, latch);

        try {
            latch.await();
        } catch (InterruptedException e) {
            log.warn("CountDownLatch阻塞已中断");
        }

        return result;
    }

    private <T> void evaluateIntervention(List<EvaluatedInterventionData> evaluatedDataList, Map<String, T> interventionMap, EvaluatorInput input, ExecutorService executor, CountDownLatch latch) {
        interventionMap.forEach((tendency, data) -> {
            executor.execute(() -> {
                try {
                    ActionData actionData = switch (data) {
                        case PhaserRecord record -> record.actionData();
                        case ActionData tempData -> tempData;
                        default -> null;
                    };
                    String prompt = buildPrompt(input.getRecentMessages(), input.getActivatedSlices(), actionData, tendency);

                    ChatResponse response = this.singleChat(prompt);
                    EvaluatedInterventionData evaluatedData = JSONObject.parseObject(response.getMessage(),
                            EvaluatedInterventionData.class);
                    synchronized (evaluatedDataList) {
                        evaluatedDataList.add(evaluatedData);
                    }
                } catch (Exception e) {
                    log.error("干预意图评估出错: {}", tendency, e);
                } finally {
                    latch.countDown();
                }
            });
        });
    }

    private String buildPrompt(List<Message> recentMessages, List<EvaluatedSlice> activatedSlices,
                               ActionData actionData, String tendency) {
        JSONObject json = new JSONObject();
        json.put("干预倾向", tendency);
        json.putArray("近期对话").addAll(recentMessages);
        json.putArray("参考记忆").addAll(activatedSlices);
        json.put("将干预的行动", JSONObject.toJSONString(actionData));
        return json.toJSONString();
    }

    @Override
    public String modelKey() {
        return "intervention_evaluator";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }
}
