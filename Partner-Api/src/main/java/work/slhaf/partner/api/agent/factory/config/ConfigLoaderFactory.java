package work.slhaf.partner.api.agent.factory.config;

import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.config.pojo.ModelConfig;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.context.ConfigFactoryContext;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigManager;
import work.slhaf.partner.api.agent.runtime.config.FileAgentConfigManager;
import work.slhaf.partner.api.chat.pojo.Message;

import java.util.HashMap;
import java.util.List;

public class ConfigLoaderFactory extends AgentBaseFactory {

    private AgentConfigManager agentConfigManager;
    private HashMap<String, ModelConfig> modelConfigMap;
    private HashMap<String, List<Message>> modelPromptMap;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        ConfigFactoryContext factoryContext = context.getConfigFactoryContext();
        modelConfigMap = factoryContext.getModelConfigMap();
        modelPromptMap = factoryContext.getModelPromptMap();

        if (AgentConfigManager.INSTANCE == null){
            AgentConfigManager.setINSTANCE(new FileAgentConfigManager());
        }

        agentConfigManager = AgentConfigManager.INSTANCE;
    }

    @Override
    protected void run() {
        agentConfigManager.load();
        agentConfigManager.check();
        modelConfigMap.putAll(agentConfigManager.getModelConfigMap());
        modelPromptMap.putAll(agentConfigManager.getModelPromptMap());
    }

}
