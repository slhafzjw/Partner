package work.slhaf.partner.api.agent.runtime.config;

import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.config.exception.PromptNotExistException;
import work.slhaf.partner.api.agent.factory.config.pojo.ModelConfig;
import work.slhaf.partner.api.chat.pojo.Message;

import java.util.HashMap;
import java.util.List;

@Slf4j
@Data
public abstract class AgentConfigLoader {

    private static final String DEFAULT_KEY = "default";
    @Setter
    public static AgentConfigLoader INSTANCE;
    protected HashMap<String, ModelConfig> modelConfigMap;
    protected HashMap<String, List<Message>> modelPromptMap;

    public void load() {
        modelConfigMap = loadModelConfig();
        modelPromptMap = loadModelPrompt();
    }

    protected abstract HashMap<String, List<Message>> loadModelPrompt();

    protected abstract HashMap<String, ModelConfig> loadModelConfig();

    public abstract void dumpModelConfig(String key);

    public List<Message> loadModelPrompt(String modelKey) {
        if (!modelPromptMap.containsKey(modelKey)) {
            throw new PromptNotExistException("不存在的modelPrompt: " + modelKey);
        }
        return modelPromptMap.get(modelKey);
    }

    public ModelConfig loadModelConfig(String modelKey) {
        if (!modelConfigMap.containsKey(modelKey)) {
            return modelConfigMap.get(DEFAULT_KEY);
        }
        return modelConfigMap.get(modelKey);
    }

    public void updateModelConfig(String modelKey, ModelConfig config) {
        modelConfigMap.put(modelKey, config);
        dumpModelConfig(modelKey);
    }

}
