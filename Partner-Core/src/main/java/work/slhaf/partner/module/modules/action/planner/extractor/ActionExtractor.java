package work.slhaf.partner.module.modules.action.planner.extractor;

import com.alibaba.fastjson2.JSONObject;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.abstracts.ActivateModel;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.module.modules.action.planner.extractor.entity.ExtractorInput;
import work.slhaf.partner.module.modules.action.planner.extractor.entity.ExtractorResult;

import java.util.List;

public class ActionExtractor extends AbstractAgentModule.Sub<ExtractorInput, ExtractorResult> implements ActivateModel {
    @InjectCapability
    private ActionCapability actionCapability;

    @Override
    public ExtractorResult execute(ExtractorInput data) {
        ExtractorResult result = new ExtractorResult();
        List<String> tendencyCache = actionCapability.selectTendencyCache(data.getInput());
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
