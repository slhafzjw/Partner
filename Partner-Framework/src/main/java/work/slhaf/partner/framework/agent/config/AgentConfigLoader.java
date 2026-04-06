package work.slhaf.partner.framework.agent.config;

import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.framework.agent.factory.config.pojo.ModelConfig;

import java.util.HashMap;

@Slf4j
@Data
public abstract class AgentConfigLoader {

    private static final String DEFAULT_KEY = "default";
    @Setter
    public static AgentConfigLoader INSTANCE;
    protected HashMap<String, ModelConfig> modelConfigMap;

    public void load() {
        modelConfigMap = loadModelConfig();
    }

    protected abstract HashMap<String, ModelConfig> loadModelConfig();

    // Keep explicit getters for Kotlin compilation phase (without Lombok-generated methods).
    public HashMap<String, ModelConfig> getModelConfigMap() {
        return modelConfigMap;
    }

    public ModelConfig loadModelConfig(String modelKey) {
        if (!modelConfigMap.containsKey(modelKey)) {
            return modelConfigMap.get(DEFAULT_KEY);
        }
        return modelConfigMap.get(modelKey);
    }
}
