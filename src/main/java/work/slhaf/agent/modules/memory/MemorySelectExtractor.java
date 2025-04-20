package work.slhaf.agent.modules.memory;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;

import java.io.IOException;

@EqualsAndHashCode(callSuper = true)
@Data
public class MemorySelectExtractor extends Model {
    public static final String MODEL_KEY = "topic_extractor";
    private static MemorySelectExtractor memorySelectExtractor;

    private MemorySelectExtractor() {
    }

    public static MemorySelectExtractor getInstance() throws IOException, ClassNotFoundException {
        if (memorySelectExtractor == null) {
            Config config = Config.getConfig();
            memorySelectExtractor = new MemorySelectExtractor();
            setModel(config, memorySelectExtractor, MODEL_KEY, ModelConstant.TOPIC_EXTRACTOR_PROMPT);
        }

        return memorySelectExtractor;
    }

    public JSONObject execute(String input) {
        return JSONObject.parseObject(singleChat(input).getMessage());
    }

    public static class Constant {
        public static final String NONE = "none";
        public static final String DATE = "date";
        public static final String TOPIC = "topic";
    }

}
