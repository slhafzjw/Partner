package work.slhaf.partner.module.modules.perceive.updater.static_extractor;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.abstracts.ActivateModel;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.module.modules.perceive.updater.static_extractor.entity.StaticMemoryExtractInput;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.util.HashMap;
import java.util.List;

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
                .userId(context.getSource())
                .messages(cognationCapability.getChatMessages())
                .existedStaticMap(perceiveCapability.getUser(context.getSource()).getStaticMemory())
                .build();
        String response = chat(List.of(new Message(Message.Character.USER, JSONUtil.toJsonPrettyStr(input))));
        JSONObject jsonObject = JSONObject.parseObject(response);
        HashMap<String, String> result = new HashMap<>();
        jsonObject.forEach((k, v) -> result.put(k, (String) v));
        return result;
    }

    @Override
    public String modelKey() {
        return "static_extractor";
    }
}
