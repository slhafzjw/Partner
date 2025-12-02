package work.slhaf.partner.module.modules.action.interventor.recognizer;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.core.action.entity.PhaserRecord;
import work.slhaf.partner.module.modules.action.interventor.recognizer.entity.MetaRecognizerResult;
import work.slhaf.partner.module.modules.action.interventor.recognizer.entity.RecognizerInput;
import work.slhaf.partner.module.modules.action.interventor.recognizer.entity.RecognizerResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

@Slf4j
@AgentSubModule
public class InterventionRecognizer extends AgentRunningSubModule<RecognizerInput, RecognizerResult> implements ActivateModel {

    @InjectCapability
    private ActionCapability actionCapability;

    @Override
    public RecognizerResult execute(RecognizerInput input) {
        // 获取必须数据
        ExecutorService executor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
        List<PhaserRecord> executingActions = input.getExecutingActions();
        List<ActionData> preparedActions = input.getPreparedActions();
        CountDownLatch countDownLatch = new CountDownLatch(executingActions.size() + preparedActions.size());

        // 创建结果容器
        RecognizerResult recognizerResult = new RecognizerResult();
        Map<String, PhaserRecord> executingInterventions = recognizerResult.getExecutingInterventions();
        Map<String, ActionData> preparedInterventions = recognizerResult.getPreparedInterventions();

        // 执行识别操作
        recognizeIntervention(executingInterventions, executingActions, executor, input, countDownLatch);
        recognizeIntervention(preparedInterventions, preparedActions, executor, input, countDownLatch);

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            log.warn("CountDownLatch阻塞已中断");
        }
        return recognizerResult;
    }

    private <T> void recognizeIntervention(Map<String, T> interventionsMap, List<T> actions, ExecutorService executor, RecognizerInput input, CountDownLatch latch) {
        for (T data : actions) {
            executor.execute(() -> {
                try {
                    String prompt = buildPrompt(data, input);
                    ChatResponse response = this.singleChat(prompt);
                    MetaRecognizerResult result = JSONObject.parseObject(response.getMessage(), MetaRecognizerResult.class);
                    if (result.isOk()) {
                        synchronized (interventionsMap) {
                            interventionsMap.put(result.getIntervention(), data);
                        }
                    }
                } catch (Exception e) {
                    log.error("LLM干预意图提取出错", e);
                } finally {
                    latch.countDown();
                }
            });
        }
    }

    private <T> String buildPrompt(T data, RecognizerInput input) {
        ActionData actionData = switch (data) {
            case PhaserRecord record -> record.actionData();
            case ActionData tempData -> tempData;
            default -> null;
        };
        if (actionData == null) {
            return null;
        }
        JSONObject json = new JSONObject();

        JSONObject actionInfo = json.putObject("行动信息");
        actionInfo.put("行动倾向", actionData.getTendency());
        actionInfo.put("行动原因", actionData.getReason());
        actionInfo.put("行动描述", actionData.getDescription());
        actionInfo.put("行动状态", actionData.getStatus());
        actionInfo.put("行动来源", actionData.getSource());

        JSONObject interactionInfo = json.putObject("交互信息");
        interactionInfo.put("用户输入", input.getInput());
        interactionInfo.put("当前对话", input.getRecentMessages());
        interactionInfo.put("近期对话", input.getUserDialogMapStr());

        return json.toString();
    }

    @Override
    public String modelKey() {
        return "intervention_recognizer";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }
}
