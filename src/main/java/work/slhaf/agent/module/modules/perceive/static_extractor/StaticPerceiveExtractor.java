package work.slhaf.agent.module.modules.perceive.static_extractor;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.module.common.Model;
import work.slhaf.agent.module.common.ModelConstant;
import work.slhaf.agent.module.modules.perceive.static_extractor.data.StaticExtractInput;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class StaticPerceiveExtractor extends Model {

    private static volatile StaticPerceiveExtractor staticPerceiveExtractor;

    public static final String MODEL_KEY = "static_extractor";


    public static StaticPerceiveExtractor getInstance() {
        if (staticPerceiveExtractor == null) {
            synchronized (StaticPerceiveExtractor.class) {
                if (staticPerceiveExtractor == null) {
                    staticPerceiveExtractor = new StaticPerceiveExtractor();
                    setModel(staticPerceiveExtractor, MODEL_KEY, ModelConstant.Prompt.MEMORY, true);
                }
            }
        }
        return staticPerceiveExtractor;
    }

    public Map<String, String> execute(StaticExtractInput input) {
        ChatResponse response = singleChat(JSONUtil.toJsonPrettyStr(input));
        JSONObject jsonObject = JSONObject.parseObject(response.getMessage());
        Map<String, String> result = new HashMap<>();
        jsonObject.forEach((k, v) -> result.put(k, (String) v));
        return result;
    }
}
