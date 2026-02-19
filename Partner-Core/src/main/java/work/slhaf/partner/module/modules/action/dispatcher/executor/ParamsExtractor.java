package work.slhaf.partner.module.modules.action.dispatcher.executor;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.ExtractorInput;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.ExtractorResult;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.HistoryAction;

import java.util.HashMap;
import java.util.List;

/**
 * 负责依据输入内容进行行动单元的参数信息提取
 */
@Slf4j
@AgentSubModule
public class ParamsExtractor extends AgentRunningSubModule<ExtractorInput, ExtractorResult> implements ActivateModel {

    @Override
    public ExtractorResult execute(ExtractorInput input) {
        String prompt = buildPrompt(input);
        ChatResponse response = this.singleChat(prompt);
        ExtractorResult result;
        try {
            result = JSONObject.parseObject(response.getMessage(), ExtractorResult.class);
        } catch (Exception e) {
            log.error("ParamsExtractor解析结果失败，返回内容：{}", response.getMessage(), e);
            result = new ExtractorResult();
            result.setOk(false);
            result.setParams(new HashMap<>());
        }
        return result;
    }

    private String buildPrompt(ExtractorInput input) {
        JSONObject prompt = new JSONObject();

        JSONObject actionData = prompt.putObject("[本次行动信息]");
        MetaActionInfo actionInfo = input.getMetaActionInfo();
        actionData.put("[行动描述]", actionInfo.getDescription());
        actionData.put("[行动参数说明]", actionInfo.getParams());

        JSONArray historyData = prompt.putArray("[历史行动执行结果]");
        List<HistoryAction> historyActions = input.getHistoryActionResults();
        for (HistoryAction historyAction : historyActions) {
            JSONObject historyItem = new JSONObject();
            historyItem.put("[行动Key]", historyAction.actionKey());
            historyItem.put("[行动描述]", historyAction.description());
            historyItem.put("[行动结果]", historyAction.result());
            historyData.add(historyItem);
        }

        JSONArray messageData = prompt.putArray("[最近消息列表]");
        messageData.addAll(input.getRecentMessages());

        return prompt.toString();
    }

    @Override
    public String modelKey() {
        return "params_extractor";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }

}
