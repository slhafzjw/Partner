package work.slhaf.partner.module.modules.action.identifier.recognizer;

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
import work.slhaf.partner.module.modules.action.identifier.recognizer.entity.MetaRecognizerResult;
import work.slhaf.partner.module.modules.action.identifier.recognizer.entity.RecognizerInput;
import work.slhaf.partner.module.modules.action.identifier.recognizer.entity.RecognizerResult;

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
        //使用LLM进行快速意图识别
        ExecutorService executor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
        RecognizerResult recognizerResult = new RecognizerResult();
        Map<String, ActionCore.PhaserRecord> resultInterventionMap = recognizerResult.getInterventions();
        List<ActionCore.PhaserRecord> executingActions = input.getExecutingActions();
        CountDownLatch countDownLatch = new CountDownLatch(executingActions.size());
        for (ActionCore.PhaserRecord record : executingActions) {
            executor.execute(() -> {
                try {
                    String prompt = buildPrompt(record, input);
                    ChatResponse response = this.singleChat(prompt);
                    MetaRecognizerResult result = JSONObject.parseObject(response.getMessage(), MetaRecognizerResult.class);
                    if (result.isOk()) {
                        synchronized (resultInterventionMap) {
                            resultInterventionMap.put(result.getIntervention(), record);
                        }
                    }
                } catch (Exception e) {
                    log.error("LLM干预意图提取出错", e);
                } finally {
                    countDownLatch.countDown();
                }
            });
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            log.warn("CountDownLatch阻塞已中断");
        }
        return recognizerResult;
    }

    private String buildPrompt(ActionCore.PhaserRecord record, RecognizerInput input) {
        ActionData actionData = record.actionData();
        JSONObject json = new JSONObject();

        JSONObject actionInfo = json.putObject("行动信息");
        actionInfo.put("行动倾向", actionData.getStatus());
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
