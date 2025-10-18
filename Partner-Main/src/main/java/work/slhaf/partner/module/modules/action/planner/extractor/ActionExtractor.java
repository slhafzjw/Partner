package work.slhaf.partner.module.modules.action.planner.extractor;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.module.modules.action.planner.extractor.entity.ExtractorInput;
import work.slhaf.partner.module.modules.action.planner.extractor.entity.ExtractorResult;

import java.util.List;

@Slf4j
@AgentSubModule
public class ActionExtractor extends AgentRunningSubModule<ExtractorInput, ExtractorResult> implements ActivateModel {

    @InjectCapability
    private ActionCapability actionCapability;

    @Override
    public ExtractorResult execute(ExtractorInput data) {
        ExtractorResult result = new ExtractorResult();
        List<String> tendencyCache = actionCapability.selectTendencyCache(data.getInput());
        result.setCacheEnabled(tendencyCache != null);
        if (tendencyCache != null && !tendencyCache.isEmpty()) {
            result.setTendencies(tendencyCache);
            return result;
        }

        for (int i = 0; i < 3; i++) {
            try {
                ChatResponse response = this.singleChat(JSONObject.toJSONString(data));
                return JSONObject.parseObject(response.getMessage(), ExtractorResult.class);
            } catch (Exception e) {
                log.error("[ActionExtractor] 提取信息出错", e);
            }
        }

        return new ExtractorResult();
    }

    @Override
    public String modelKey() {
        return "action_extractor";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }
}
