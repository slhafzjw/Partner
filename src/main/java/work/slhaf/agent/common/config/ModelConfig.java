package work.slhaf.agent.common.config;

import cn.hutool.json.JSONUtil;
import lombok.Data;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

@Data
public class ModelConfig {

    private static final String MODEL_CONFIG_DIR_PATH = "./config/model/";
    private static final HashMap<String, ModelConfig> modelConfigMap = new HashMap<>();

    private String apikey;
    private String baseUrl;
    private String model;

    public void generateConfig(String filename) throws IOException {
        String str = JSONUtil.toJsonPrettyStr(this);
        File file = new File(MODEL_CONFIG_DIR_PATH + filename + ".json");
        FileUtils.writeStringToFile(file, str, StandardCharsets.UTF_8);
    }

    public static ModelConfig load(String modelKey) {
        if (!modelConfigMap.containsKey(modelKey)) {
            modelConfigMap.put(modelKey,loadConfig(modelKey));
        }

        return modelConfigMap.get(modelKey);
    }

    private static ModelConfig loadConfig(String modelKey) {
        File file = new File(MODEL_CONFIG_DIR_PATH+modelKey+".json");
        return JSONUtil.readJSONObject(file,StandardCharsets.UTF_8).toBean(ModelConfig.class);
    }
}
