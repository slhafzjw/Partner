package work.slhaf.partner.module.modules.perceive.updater.static_extractor;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.module.abstracts.ActivateModel;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.module.modules.perceive.updater.static_extractor.entity.StaticMemoryExtractInput;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.util.HashMap;
@EqualsAndHashCode(callSuper = true)
@Data
public class StaticMemoryExtractor extends AbstractAgentModule.Sub<PartnerRunningFlowContext, HashMap<String, String>> implements ActivateModel {
    @InjectCapability
    private CognationCapability cognationCapability;
    @InjectCapability
    private PerceiveCapability perceiveCapability;
    @Override
    public HashMap<String, String> execute(PartnerRunningFlowContext context) {
        StaticMemoryExtractInput input = StaticMemoryExtractInput.builder()
                .userId(context.getUserId())
                .messages(cognationCapability.getChatMessages())
                .existedStaticMap(perceiveCapability.getUser(context.getUserId()).getStaticMemory())
                .build();
        ChatResponse response = singleChat(JSONUtil.toJsonPrettyStr(input));
        JSONObject jsonObject = JSONObject.parseObject(response.getMessage());
        HashMap<String, String> result = new HashMap<>();
        jsonObject.forEach((k, v) -> result.put(k, (String) v));
        return result;
    }
    @Override
    public String modelKey() {
        return "static_extractor";
    }
    @Override
    public boolean withBasicPrompt() {
        return true;
    }
}
