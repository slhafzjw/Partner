package work.slhaf.agent.module.modules.perceive.updater.static_extractor;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.module.common.Model;
import work.slhaf.agent.module.common.ModelConstant;
import work.slhaf.agent.module.modules.perceive.updater.static_extractor.data.StaticExtractInput;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class StaticExtractor extends Model {

    private static volatile StaticExtractor staticExtractor;


    public static StaticExtractor getInstance() {
        if (staticExtractor == null) {
            synchronized (StaticExtractor.class) {
                if (staticExtractor == null) {
                    staticExtractor = new StaticExtractor();
                    setModel(staticExtractor, ModelConstant.Prompt.MEMORY, true);
                }
            }
        }
        return staticExtractor;
    }

    public Map<String, String> execute(StaticExtractInput input) {
        ChatResponse response = singleChat(JSONUtil.toJsonPrettyStr(input));
        JSONObject jsonObject = JSONObject.parseObject(response.getMessage());
        Map<String, String> result = new HashMap<>();
        jsonObject.forEach((k, v) -> result.put(k, (String) v));
        return result;
    }

    @Override
    protected String modelKey() {
        return "static_extractor";
    }
}
