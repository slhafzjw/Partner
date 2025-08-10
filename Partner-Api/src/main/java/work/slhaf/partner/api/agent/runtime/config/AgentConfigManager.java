package work.slhaf.partner.api.agent.runtime.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.config.exception.ConfigNotExistException;
import work.slhaf.partner.api.agent.factory.config.exception.ConfigUpdateFailedException;
import work.slhaf.partner.api.agent.factory.config.exception.PromptNotExistException;
import work.slhaf.partner.api.agent.factory.config.pojo.ModelConfig;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.chat.pojo.Message;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public abstract class AgentConfigManager {

    @Setter
    public static AgentConfigManager INSTANCE;
    private static final String DEFAULT_KEY = "default";

    @Getter
    protected HashMap<String, ModelConfig> modelConfigMap;
    @Getter
    protected HashMap<String, List<Message>> modelPromptMap;

    @Getter
    @Setter
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

    public List<MetaModule> moduleEnabledStatusFilter(List<MetaModule> moduleList) {
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
        return moduleList;
    }

    /**
     * 对模型Config与Prompt分别进行检验,除了都必须包含default外，还需要确保数量、key一致，毕竟是模型配置与提示词
     */
    public void check() {
        log.info("[AgentConfigManager]: 执行config与prompt检测...");
        if (!modelConfigMap.containsKey("default")) {
            throw new ConfigNotExistException("缺少默认配置! 需确保存在一个模型配置的key为`default`");
        }
        if (!modelPromptMap.containsKey("basic")) {
            throw new PromptNotExistException("缺少基础Prompt! 需要确保存在key为basic的Prompt文件，它将与其他Prompt共同作用于模块节点。");
        }
        Set<String> configKeySet = new HashSet<>(modelConfigMap.keySet());
        configKeySet.remove("default");
        Set<String> promptKeySet = new HashSet<>(modelPromptMap.keySet());
        promptKeySet.remove("basic");
        if (!promptKeySet.containsAll(configKeySet)) {
            log.warn("存在未被提示词包含的模型配置，该配置将无法生效!");
        }
        log.info("[AgentConfigManager]: 检测完毕.");
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
