package work.slhaf.partner.module.modules.action.interventor.recognizer;

import com.alibaba.fastjson2.JSONObject;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.module.abstracts.ActivateModel;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.ExecutableAction;
import work.slhaf.partner.module.modules.action.interventor.recognizer.entity.MetaRecognizerResult;
import work.slhaf.partner.module.modules.action.interventor.recognizer.entity.RecognizerInput;
import work.slhaf.partner.module.modules.action.interventor.recognizer.entity.RecognizerResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

public class InterventionRecognizer extends AbstractAgentModule.Sub<RecognizerInput, RecognizerResult> implements ActivateModel {
    @InjectCapability
    private ActionCapability actionCapability;
    @Override
    public RecognizerResult execute(RecognizerInput input) {
        // 获取必须数据
        ExecutorService executor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
        List<ExecutableAction> executingActions = input.getExecutingActions();
        List<ExecutableAction> preparedActions = input.getPreparedActions();
        CountDownLatch countDownLatch = new CountDownLatch(executingActions.size() + preparedActions.size());
        // 创建结果容器
        RecognizerResult recognizerResult = new RecognizerResult();
        Map<String, ExecutableAction> executingInterventions = recognizerResult.getExecutingInterventions();
        Map<String, ExecutableAction> preparedInterventions = recognizerResult.getPreparedInterventions();
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
    private void recognizeIntervention(Map<String, ExecutableAction> interventionsMap, List<ExecutableAction> actions, ExecutorService executor, RecognizerInput input, CountDownLatch latch) {
        for (ExecutableAction data : actions) {
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
    private String buildPrompt(ExecutableAction executableAction, RecognizerInput input) {
        JSONObject json = new JSONObject();
        JSONObject actionInfo = json.putObject("行动信息");
        actionInfo.put("行动倾向", executableAction.getTendency());
        actionInfo.put("行动原因", executableAction.getReason());
        actionInfo.put("行动描述", executableAction.getDescription());
        actionInfo.put("行动状态", executableAction.getStatus());
        actionInfo.put("行动来源", executableAction.getSource());
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
