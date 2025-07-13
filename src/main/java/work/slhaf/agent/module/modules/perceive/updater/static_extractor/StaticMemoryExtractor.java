package work.slhaf.agent.module.modules.perceive.updater.static_extractor;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.core.cognation.capability.ability.CognationCapability;
import work.slhaf.agent.core.cognation.CognationManager;
import work.slhaf.agent.core.cognation.capability.ability.PerceiveCapability;
import work.slhaf.agent.core.interaction.data.context.InteractionContext;
import work.slhaf.agent.module.common.Model;
import work.slhaf.agent.module.common.ModelConstant;
import work.slhaf.agent.module.modules.perceive.updater.static_extractor.data.StaticMemoryExtractInput;

import java.io.IOException;
import java.util.HashMap;

@EqualsAndHashCode(callSuper = true)
@Data
public class StaticMemoryExtractor extends Model {

    private static volatile StaticMemoryExtractor staticMemoryExtractor;

    private CognationCapability cognationCapability;
    private PerceiveCapability perceiveCapability;

    public static StaticMemoryExtractor getInstance() throws IOException, ClassNotFoundException {
        if (staticMemoryExtractor == null) {
            synchronized (StaticMemoryExtractor.class) {
                if (staticMemoryExtractor == null) {
                    staticMemoryExtractor = new StaticMemoryExtractor();
                    staticMemoryExtractor.setCognationCapability(CognationManager.getInstance());
                    staticMemoryExtractor.setPerceiveCapability(CognationManager.getInstance());
                    setModel(staticMemoryExtractor, ModelConstant.Prompt.MEMORY, true);
                }
            }
        }
        return staticMemoryExtractor;
    }

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
    protected String modelKey() {
        return "static_extractor";
    }
}
