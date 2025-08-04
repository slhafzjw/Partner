package work.slhaf.partner.api.factory.config;

import lombok.Setter;
import work.slhaf.partner.api.common.chat.pojo.Message;
import work.slhaf.partner.api.factory.AgentBaseFactory;
import work.slhaf.partner.api.factory.config.pojo.ModelConfig;
import work.slhaf.partner.api.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.factory.context.ConfigFactoryContext;

import java.util.HashMap;
import java.util.List;

public class ConfigLoaderFactory extends AgentBaseFactory {

    @Setter
    private static ModelConfigManager modelConfigManager = new DefaultModelConfigManager();
    private HashMap<String, ModelConfig> modelConfigMap;
    private HashMap<String, List<Message>> modelPromptMap;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        ConfigFactoryContext factoryContext = context.getConfigFactoryContext();
        modelConfigMap = factoryContext.getModelConfigMap();
        modelPromptMap = factoryContext.getModelPromptMap();
    }

    @Override
    protected void run() {
        modelConfigManager.load();
        modelConfigManager.check();
        modelConfigMap.putAll(modelConfigManager.getModelConfigMap());
        modelPromptMap.putAll(modelConfigManager.getModelPromptMap());
    }

}
