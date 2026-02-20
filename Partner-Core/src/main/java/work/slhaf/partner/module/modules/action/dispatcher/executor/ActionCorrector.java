package work.slhaf.partner.module.modules.action.dispatcher.executor;

import com.alibaba.fastjson2.JSONObject;
import lombok.val;
import work.slhaf.partner.api.agent.factory.module.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.module.abstracts.ActivateModel;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.CorrectorInput;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.CorrectorResult;

/**
 * 负责在单组行动执行后，根据行动意图与结果检查后续行动是否符合目的，必要时直接调整行动链，或发起自对话请求进行干预
 */
public class ActionCorrector extends AbstractAgentModule.Sub<CorrectorInput, CorrectorResult> implements ActivateModel {
    @Override
    public CorrectorResult execute(CorrectorInput input) {
        val prompt = buildPrompt(input);
        val chatResponse = singleChat(prompt);
        return JSONObject.parseObject(chatResponse.getMessage(), CorrectorResult.class);
    }

    private String buildPrompt(CorrectorInput input) {
        val prompt = new JSONObject();
        prompt.put("[行动来源]", input.getSource());
        prompt.put("[行动倾向]", input.getTendency());
        prompt.put("[行动描述]", input.getDescription());
        prompt.put("[行动原因]", input.getReason());
        val messages = prompt.putArray("[近期对话]");
        messages.addAll(input.getRecentMessages());
        val memory = prompt.putArray("[已激活记忆]");
        memory.addAll(input.getActivatedSlices());
        val history = prompt.putArray("[已执行情况]");
        history.addAll(input.getHistory());
        return prompt.toJSONString();
    }

    @Override
    public String modelKey() {
        return "action_corrector";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }
}
