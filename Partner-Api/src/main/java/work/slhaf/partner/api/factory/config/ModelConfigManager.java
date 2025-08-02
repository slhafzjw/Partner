package work.slhaf.partner.api.factory.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.common.chat.pojo.Message;
import work.slhaf.partner.api.factory.config.exception.ModelConfigNotExistException;
import work.slhaf.partner.api.factory.config.exception.ModelPromptNotExistException;
import work.slhaf.partner.api.factory.config.pojo.ModelConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public abstract class ModelConfigManager {

    public static ModelConfigManager INSTANCE;
    @Getter
    protected HashMap<String, ModelConfig> modelConfigMap;
    @Getter
    protected HashMap<String, List<Message>> modelPromptMap;

    protected ModelConfigManager() {
        INSTANCE = this;
    }

    public void load() {
        modelConfigMap = loadConfig();
        modelPromptMap = loadPrompt();
    }

    protected abstract HashMap<String, List<Message>> loadPrompt();

    protected abstract HashMap<String, ModelConfig> loadConfig();

    /**
     * 对模型Config与Prompt分别进行检验,除了都必须包含default外，还需要确保数量、key一致，毕竟是模型配置与提示词
     */
    public void check(){
        log.info("[ModelConfigManager]: 执行config与prompt检测...");
        if (!modelConfigMap.containsKey("default")){
            throw new ModelConfigNotExistException("缺少默认配置! 需确保存在一个模型配置的key为`default`");
        }
        if (!modelPromptMap.containsKey("basic")){
            throw new ModelPromptNotExistException("缺少基础Prompt! 需要确保存在key为basic的Prompt文件，它将与其他Prompt共同作用于模块节点。");
        }
        Set<String> configKeySet = new HashSet<>(modelConfigMap.keySet());
        configKeySet.remove("default");
        Set<String> promptKeySet = new HashSet<>(modelPromptMap.keySet());
        promptKeySet.remove("basic");
        if (!promptKeySet.containsAll(configKeySet)){
            log.warn("存在未被提示词包含的模型配置，该配置将无法生效!");
        }
        log.info("[ModelConfigManager]: 检测完毕.");
    }


    public List<Message> loadModelPrompt(String modelKey){
        if (!modelPromptMap.containsKey(modelKey)){
            throw new ModelPromptNotExistException("不存在的modelPrompt: "+modelKey);
        }
        return modelPromptMap.get(modelKey);
    }

    public ModelConfig loadModelConfig(String modelKey) {
        if (!modelConfigMap.containsKey(modelKey)) {
            throw new ModelConfigNotExistException("不存在的modelKey: " + modelKey);
        }
        return modelConfigMap.get(modelKey);
    }

    public void updateModelConfig(String modelKey, ModelConfig config) {
        if (!modelConfigMap.containsKey(modelKey)) {
            throw new ModelConfigNotExistException("不存在的modelKey: " + modelKey);
        }
        modelConfigMap.get(modelKey).setModel(config.getModel());
        modelConfigMap.get(modelKey).setBaseUrl(config.getBaseUrl());
        modelConfigMap.get(modelKey).setApikey(config.getApikey());
    }
}
