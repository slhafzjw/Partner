package work.slhaf.partner.api.agent.runtime.config;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import work.slhaf.partner.api.agent.factory.config.exception.*;
import work.slhaf.partner.api.agent.factory.config.pojo.ModelConfig;
import work.slhaf.partner.api.agent.factory.config.pojo.PrimaryModelConfig;
import work.slhaf.partner.api.agent.factory.config.pojo.PrimaryModelPrompt;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.chat.pojo.Message;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

/**
 * 默认配置工厂
 * 将从当前运行目录的config文件夹下创建并读取配置
 */
@Slf4j
public class FileAgentConfigManager extends AgentConfigManager {

    protected static final String CONFIG_DIR = "./config/";
    protected static final String MODEL_CONFIG_DIR = "./config/model/";
    protected static final String PROMPT_CONFIG_DIR = "./config/prompt/";
    protected static final String MODULE_ENABLED_STATUS_CONFIG_FILE = CONFIG_DIR + "module_enabled_status.json";


    @Override
    protected HashMap<String, List<Message>> loadModelPrompt() {
        File file = new File(PROMPT_CONFIG_DIR);
        if (!file.exists() && !file.isDirectory()) {
            throw new PromptDirNotExistException("未找到提示词目录: " + PROMPT_CONFIG_DIR + " 请手动创建!");
        }
        File[] files = file.listFiles();
        if (files == null || files.length == 0) {
            throw new PromptNotExistException("在目录 " + PROMPT_CONFIG_DIR + " 中未找到提示词配置!");
        }
        HashMap<String, List<Message>> promptMap = new HashMap<>();
        for (File f : files) {
            if (f.isDirectory()) {
                continue;
            }
            PrimaryModelPrompt primaryModelPrompt = JSONUtil.readJSONObject(f, StandardCharsets.UTF_8).toBean(PrimaryModelPrompt.class);
            promptMap.put(primaryModelPrompt.getKey(), primaryModelPrompt.getMessages());
        }
        return promptMap;
    }

    @Override
    protected HashMap<String, ModelConfig> loadModelConfig() {
        File file = new File(MODEL_CONFIG_DIR);
        if (!file.exists() || !file.isDirectory()) {
            throw new ConfigDirNotExistException("未找到配置目录: " + MODEL_CONFIG_DIR + " 请手动创建!");
        }
        File[] files = file.listFiles();
        if (files == null || files.length == 0) {
            throw new ConfigNotExistException("在目录" + MODEL_CONFIG_DIR + "中未找到配置文件!");
        }
        //遍历文件获取所有配置文件并返回
        HashMap<String, ModelConfig> configMap = new HashMap<>();
        for (File f : files) {
            if (f.isDirectory()) {
                continue;
            }
            PrimaryModelConfig primaryModelConfig = JSONUtil.readJSONObject(f, StandardCharsets.UTF_8).toBean(PrimaryModelConfig.class);
            configMap.put(primaryModelConfig.getKey(), primaryModelConfig.getModelConfig());
        }
        return configMap;
    }

    @Override
    protected HashMap<String, Boolean> loadModuleEnabledStatusMap(List<MetaModule> moduleList) {
        File file = new File(MODULE_ENABLED_STATUS_CONFIG_FILE);
        try {
            moduleEnabledStatus = new HashMap<>();
            if (!file.exists()) {
                file.createNewFile();
                for (MetaModule module : moduleList) {
                    moduleEnabledStatus.put(module.getName(), module.isEnabled());
                }
                dumpModuleEnabledStatus();
            } else {
                JSONObject obj = JSONUtil.readJSONObject(file, StandardCharsets.UTF_8);
                for (String s : obj.keySet()) {
                    moduleEnabledStatus.put(s, obj.getBool(s));
                }
                log.info("ModuleEnabledStatusConfig 配置文件已成功读取!");
            }
            return moduleEnabledStatus;
        } catch (Exception e) {
            throw new ConfigGenerateFailedException("ModuleEnabledStatusConfig 配置文件创建失败!", e);
        }
    }

    @Override
    public void dumpModelConfig(String key) {
        try {
            File file = new File(MODEL_CONFIG_DIR + key + ".json");
            if (!file.exists()) {
                file.createNewFile();
            }
            FileUtils.writeStringToFile(file, JSONUtil.toJsonPrettyStr(modelConfigMap.get(key)), StandardCharsets.UTF_8, false);
        } catch (Exception e) {
            throw new ConfigUpdateFailedException("ModelConfig 配置文件更新失败!");
        }
    }

    @Override
    protected void dumpModuleEnabledStatus() {
        try {
            File file = new File(MODULE_ENABLED_STATUS_CONFIG_FILE);
            FileUtils.writeStringToFile(file, JSONUtil.toJsonPrettyStr(moduleEnabledStatus), StandardCharsets.UTF_8, false);
        } catch (IOException e) {
            throw new ConfigGenerateFailedException("ModuleEnabledStatus 配置文件更新失败!");
        }
    }
}
