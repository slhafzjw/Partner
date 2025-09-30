package work.slhaf.partner.api.agent.runtime.config;

import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.config.exception.ConfigUpdateFailedException;
import work.slhaf.partner.api.agent.factory.config.exception.PromptNotExistException;
import work.slhaf.partner.api.agent.factory.config.pojo.ModelConfig;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.chat.pojo.Message;

import java.util.HashMap;
import java.util.List;

@Slf4j
@Data
public abstract class AgentConfigManager {

    @Setter
    public static AgentConfigManager INSTANCE = new FileAgentConfigManager();
    private static final String DEFAULT_KEY = "default";

    protected HashMap<String, ModelConfig> modelConfigMap;
    protected HashMap<String, List<Message>> modelPromptMap;
    protected HashMap<String, Boolean> moduleEnabledStatus;
    protected List<MetaModule> moduleList;

    public void load() {
        modelConfigMap = loadModelConfig();
        modelPromptMap = loadModelPrompt();
    }

    protected abstract HashMap<String, List<Message>> loadModelPrompt();

    protected abstract HashMap<String, ModelConfig> loadModelConfig();

    public abstract void dumpModelConfig(String key);

    protected abstract void dumpModuleEnabledStatus();

    protected abstract HashMap<String, Boolean> loadModuleEnabledStatusMap();

    public void moduleEnabledStatusFilterAndRecord(List<MetaModule> moduleList) {
        this.moduleList = moduleList;
        this.moduleEnabledStatus = loadModuleEnabledStatusMap();

        boolean unmatch = false;
        for (MetaModule metaModule : moduleList) {
            String moduleName = metaModule.getName();
            if (moduleEnabledStatus.containsKey(moduleName)) {
                metaModule.setEnabled(moduleEnabledStatus.get(moduleName));
            } else {
                log.warn("缺少Module {} 启用配置! 将触发更新操作!", moduleName);
                unmatch = true;
            }
        }
        if (unmatch) {
            dumpModuleEnabledStatus();
        }
    }

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

    public void updateModuleEnabledStatus(String key, boolean status) {
        if (!moduleEnabledStatus.containsKey(key)) {
            throw new ConfigUpdateFailedException("模块状态更新失败! 不存在的ModuleKey: " + key);
        }
        moduleEnabledStatus.put(key, status);
        dumpModuleEnabledStatus();
        for (MetaModule metaModule : moduleList) {
            if (metaModule.getName().equals(key)) {
                metaModule.setEnabled(status);
                break;
            }
        }
    }

}
