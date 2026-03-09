package work.slhaf.partner.module.modules.action.planner.confirmer;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.abstracts.ActivateModel;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.ExecutableAction;
import work.slhaf.partner.core.action.entity.PendingActionRecord;
import work.slhaf.partner.module.modules.action.planner.confirmer.entity.ConfirmerInput;
import work.slhaf.partner.module.modules.action.planner.confirmer.entity.ConfirmerResult;
import work.slhaf.partner.module.modules.action.planner.confirmer.entity.PendingDecisionItem;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

public class ActionConfirmer extends AbstractAgentModule.Sub<ConfirmerInput, ConfirmerResult> implements ActivateModel {
    @InjectCapability
    private ActionCapability actionCapability;

    @Override
    public ConfirmerResult execute(ConfirmerInput data) {
        List<PendingActionRecord> pendingActions = data.getPendingActions();
        if (pendingActions == null || pendingActions.isEmpty()) {
            return new ConfirmerResult();
        }
        ExecutorService executor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
        CountDownLatch latch = new CountDownLatch(pendingActions.size());
        ConfirmerResult result = new ConfirmerResult();
        List<PendingDecisionItem> decisions = result.getDecisions();
        for (PendingActionRecord pendingAction : pendingActions) {
            executor.execute(() -> {
                try {
                    ExecutableAction executableAction = pendingAction.getExecutableAction();
                    String prompt = buildPrompt(executableAction, data.getInput(), data.getRecentMessages());
                    DecisionResponse tempResult = formattedChat(
                            List.of(new Message(Message.Character.USER, prompt)),
                            DecisionResponse.class
                    );
                    PendingActionRecord.Decision decision = parseDecision(tempResult);
                    String reason = tempResult.getReason();
                    synchronized (decisions) {
                        decisions.add(new PendingDecisionItem(pendingAction.getPendingId(), decision, reason));
                    }
                } catch (Exception e) {
                    synchronized (decisions) {
                        decisions.add(new PendingDecisionItem(
                                pendingAction.getPendingId(),
                                PendingActionRecord.Decision.HOLD,
                                "确认解析失败: " + e.getLocalizedMessage()
                        ));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.warn("CountDownLatch阻塞已中断");
        }
        return result;
    }

    private PendingActionRecord.Decision parseDecision(DecisionResponse tempResult) {
        if (tempResult == null) {
            return PendingActionRecord.Decision.HOLD;
        }
        String decisionText = tempResult.getDecision();
        if (decisionText != null) {
            String upperDecision = decisionText.toUpperCase();
            if (upperDecision.contains("CONFIRM")) {
                return PendingActionRecord.Decision.CONFIRM;
            }
            if (upperDecision.contains("REJECT")) {
                return PendingActionRecord.Decision.REJECT;
            }
            if (upperDecision.contains("HOLD")) {
                return PendingActionRecord.Decision.HOLD;
            }
        }
        Boolean confirmed = tempResult.getConfirmed();
        if (Boolean.TRUE.equals(confirmed)) {
            return PendingActionRecord.Decision.CONFIRM;
        }
        return PendingActionRecord.Decision.HOLD;
    }

    private String buildPrompt(ExecutableAction data, String input, List<Message> recentMessages) {
        JSONObject prompt = new JSONObject();
        prompt.put("[用户输入]", input);
        JSONObject actionData = prompt.putObject("[行动数据]");
        actionData.put("[行动倾向]", data.getTendency());
        actionData.put("[行动原因]", data.getReason());
        actionData.put("[行动来源]", data.getSource());
        actionData.put("[行动描述]", data.getDescription());
        JSONArray decisionEnums = prompt.putArray("[决策选项]");
        decisionEnums.add("CONFIRM");
        decisionEnums.add("REJECT");
        decisionEnums.add("HOLD");
        JSONArray messageData = prompt.putArray("[近期对话]");
        if (recentMessages != null) {
            messageData.addAll(recentMessages);
        }
        return prompt.toString();
    }

    @Override
    public String modelKey() {
        return "action-confirmer";
    }

    @lombok.Data
    private static class DecisionResponse {
        private String decision;
        private String reason;
        private Boolean confirmed;
    }
}
