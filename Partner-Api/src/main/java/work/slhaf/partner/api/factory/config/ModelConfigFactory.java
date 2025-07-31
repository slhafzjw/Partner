package work.slhaf.partner.api.factory.config;

import work.slhaf.partner.api.common.chat.pojo.Message;
import work.slhaf.partner.api.factory.config.exception.UnExistModelConfigException;
import work.slhaf.partner.api.factory.config.exception.UnExistModelPromptException;
import work.slhaf.partner.api.factory.config.pojo.ModelConfig;

import java.util.HashMap;
import java.util.List;

public abstract class ModelConfigFactory {

    public static ModelConfigFactory factory;
    protected HashMap<String, ModelConfig> modelConfigMap;
    protected HashMap<String, List<Message>> modelPromptMap;

    public ModelConfigFactory() {
        factory = this;
    }

    public void load() {
        modelConfigMap = loadConfig();
        modelPromptMap = loadPrompt();
    }

    protected abstract HashMap<String, List<Message>> loadPrompt();

    protected abstract HashMap<String, ModelConfig> loadConfig();


    public List<Message> loadModelPrompt(String modelKey){
        if (!modelPromptMap.containsKey(modelKey)){
            throw new UnExistModelPromptException("不存在的modelPrompt: "+modelKey);
        }
        return modelPromptMap.get(modelKey);
    }

    public ModelConfig loadModelConfig(String modelKey) {
        if (!modelConfigMap.containsKey(modelKey)) {
            throw new UnExistModelConfigException("不存在的modelKey: " + modelKey);
        }
        return modelConfigMap.get(modelKey);
    }

    public void updateModelConfig(String modelKey, ModelConfig config) {
        if (!modelConfigMap.containsKey(modelKey)) {
            throw new UnExistModelConfigException("不存在的modelKey: " + modelKey);
        }
        modelConfigMap.get(modelKey).setModel(config.getModel());
        modelConfigMap.get(modelKey).setBaseUrl(config.getBaseUrl());
        modelConfigMap.get(modelKey).setApikey(config.getApikey());
    }
}
