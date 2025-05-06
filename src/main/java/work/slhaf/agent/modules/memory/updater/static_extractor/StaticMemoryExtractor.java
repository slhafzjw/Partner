package work.slhaf.agent.modules.memory.updater.static_extractor;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;
import work.slhaf.agent.modules.memory.updater.static_extractor.data.StaticMemoryExtractInput;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class StaticMemoryExtractor extends Model {

    private static StaticMemoryExtractor staticMemoryExtractor;

    private static final String MODEL_KEY = "static_memory_extractor";


    public static StaticMemoryExtractor getInstance() throws IOException, ClassNotFoundException {
        if (staticMemoryExtractor == null) {
            staticMemoryExtractor = new StaticMemoryExtractor();
            setModel(Config.getConfig(), staticMemoryExtractor, MODEL_KEY, ModelConstant.STATIC_MEMORY_EXTRACTOR_PROMPT);
        }
        return staticMemoryExtractor;
    }

    public Map<String, String> execute(StaticMemoryExtractInput input) {
        ChatResponse response = singleChat(JSONUtil.toJsonPrettyStr(input));
        JSONObject jsonObject = JSONObject.parseObject(response.getMessage());
        Map<String, String> result = new HashMap<>();
        jsonObject.forEach((k, v) -> {
            result.put(k, (String) v);
        });
        return result;
    }
}
