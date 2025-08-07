package work.slhaf.partner.api.agent.factory.config;

import cn.hutool.json.JSONUtil;
import work.slhaf.partner.api.agent.factory.config.exception.ModelConfigDirNotExistException;
import work.slhaf.partner.api.agent.factory.config.exception.ModelConfigNotExistException;
import work.slhaf.partner.api.agent.factory.config.exception.ModelPromptDirNotExistException;
import work.slhaf.partner.api.agent.factory.config.exception.ModelPromptNotExistException;
import work.slhaf.partner.api.agent.factory.config.pojo.ModelConfig;
import work.slhaf.partner.api.agent.factory.config.pojo.PrimaryModelConfig;
import work.slhaf.partner.api.agent.factory.config.pojo.PrimaryModelPrompt;
import work.slhaf.partner.api.chat.pojo.Message;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

/**
 * 默认配置工厂
 * 将从当前运行目录的config文件夹下创建并读取配置
 */
public class DefaultModelConfigManager extends ModelConfigManager {

    private static final String MODEL_CONFIG_DIR = "./config/model/";
    private static final String PROMPT_CONFIG_DIR = "./config/prompt/";


    @Override
    protected HashMap<String, List<Message>> loadPrompt() {
        File file = new File(PROMPT_CONFIG_DIR);
        if (!file.exists() && !file.isDirectory()) {
            throw new ModelPromptDirNotExistException("未找到提示词目录: " + PROMPT_CONFIG_DIR + " 请手动创建!");
        }
        File[] files = file.listFiles();
        if (files == null || files.length == 0) {
            throw new ModelPromptNotExistException("在目录 " + PROMPT_CONFIG_DIR + " 中未找到提示词配置!");
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
    protected HashMap<String, ModelConfig> loadConfig() {
        File file = new File(MODEL_CONFIG_DIR);
        if (!file.exists() || !file.isDirectory()) {
            throw new ModelConfigDirNotExistException("未找到配置目录: " + MODEL_CONFIG_DIR + " 请手动创建!");
        }
        File[] files = file.listFiles();
        if (files == null || files.length == 0) {
            throw new ModelConfigNotExistException("在目录" + MODEL_CONFIG_DIR + "中未找到配置文件!");
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
}
