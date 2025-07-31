package work.slhaf.partner.api.factory.config;

import work.slhaf.partner.api.common.chat.pojo.Message;
import work.slhaf.partner.api.factory.config.pojo.ModelConfig;

import java.util.HashMap;
import java.util.List;

/**
 * 默认配置工厂
 * 将从当前运行目录的config文件夹下创建并读取配置
 */
public class DefaultModelConfigFactory extends ModelConfigFactory {

    private static final String MODEL_CONFIG_DIR = "./config/model/";
    private static final String PROMPT_CONFIG_DIR = "./config/prompt/";


    @Override
    protected HashMap<String, List<Message>> loadPrompt() {

        return null;
    }

    @Override
    protected HashMap<String, ModelConfig> loadConfig() {
        return null;
    }
}
