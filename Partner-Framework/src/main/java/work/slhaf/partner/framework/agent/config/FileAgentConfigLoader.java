package work.slhaf.partner.framework.agent.config;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.framework.agent.factory.config.exception.ConfigDirNotExistException;
import work.slhaf.partner.framework.agent.factory.config.exception.ConfigNotExistException;
import work.slhaf.partner.framework.agent.factory.config.pojo.ModelConfig;
import work.slhaf.partner.framework.agent.factory.config.pojo.PrimaryModelConfig;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * 默认配置工厂
 * 将从当前运行目录的config文件夹下创建并读取配置
 */
@Slf4j
public class FileAgentConfigLoader extends AgentConfigLoader {

    protected static final String CONFIG_DIR = "./config/";
    protected static final String MODEL_CONFIG_DIR = "./config/model/";

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
}
