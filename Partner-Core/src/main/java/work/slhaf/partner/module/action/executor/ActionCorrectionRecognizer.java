package work.slhaf.partner.module.action.executor;

import com.alibaba.fastjson2.JSONObject;
import lombok.val;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.abstracts.ActivateModel;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.module.action.executor.entity.CorrectionRecognizerInput;
import work.slhaf.partner.module.action.executor.entity.CorrectionRecognizerResult;

import java.util.List;

/**
 * 负责在行动链执行过程中判断当前进度是否异常，是否需要引入 corrector 介入。
 */
public class ActionCorrectionRecognizer extends AbstractAgentModule.Sub<CorrectionRecognizerInput, CorrectionRecognizerResult> implements ActivateModel {
    @Override
    public CorrectionRecognizerResult execute(CorrectionRecognizerInput input) {
        val prompt = buildPrompt(input);
        return formattedChat(List.of(new Message(Message.Character.USER, prompt)), CorrectionRecognizerResult.class);
    }

    private String buildPrompt(CorrectionRecognizerInput input) {
        val prompt = new JSONObject();
        prompt.put("[行动来源]", input.getSource());
        prompt.put("[行动倾向]", input.getTendency());
        prompt.put("[行动描述]", input.getDescription());
        prompt.put("[行动原因]", input.getReason());
        prompt.put("[当前阶段]", input.getCurrentStage());
        prompt.put("[当前阶段位置]", input.getCurrentStageIndex());
        prompt.put("[是否最后阶段]", input.isLastStage());
        val stageList = prompt.putArray("[当前行动链阶段列表]");
        stageList.addAll(input.getOrderedStages());
        val history = prompt.putArray("[当前阶段已执行情况]");
        if (input.getHistory() != null) {
            history.addAll(input.getHistory());
        }
        val metaActions = prompt.putArray("[当前阶段行动结果]");
        metaActions.addAll(input.getCurrentStageMetaActions());
        val messages = prompt.putArray("[近期对话]");
        messages.addAll(input.getRecentMessages());
        val memory = prompt.putArray("[已激活记忆]");
        memory.addAll(input.getActivatedSlices());
        return prompt.toJSONString();
    }

    @Override
    public String modelKey() {
        return "action_correction_recognizer";
    }
}
