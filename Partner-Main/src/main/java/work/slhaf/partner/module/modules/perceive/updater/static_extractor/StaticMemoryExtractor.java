package work.slhaf.partner.module.modules.perceive.updater.static_extractor;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.core.cognation.cognation.CognationCapability;
import work.slhaf.partner.core.cognation.submodule.perceive.PerceiveCapability;
import work.slhaf.partner.core.interaction.data.context.InteractionContext;
import work.slhaf.partner.module.modules.perceive.updater.static_extractor.data.StaticMemoryExtractInput;

import java.io.IOException;
import java.util.HashMap;

@EqualsAndHashCode(callSuper = true)
@Data
public class StaticMemoryExtractor extends AgentRunningSubModule<InteractionContext, HashMap<String, String>> implements ActivateModel {

    private static volatile StaticMemoryExtractor staticMemoryExtractor;

    @InjectCapability
    private CognationCapability cognationCapability;
    @InjectCapability
    private PerceiveCapability perceiveCapability;

    private StaticMemoryExtractor() {
        modelSettings();
    }

    public static StaticMemoryExtractor getInstance() throws IOException, ClassNotFoundException {
        if (staticMemoryExtractor == null) {
            synchronized (StaticMemoryExtractor.class) {
                if (staticMemoryExtractor == null) {
                    staticMemoryExtractor = new StaticMemoryExtractor();
                }
            }
        }
        return staticMemoryExtractor;
    }

    @Override
    public HashMap<String, String> execute(InteractionContext context) {
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
