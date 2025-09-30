package work.slhaf.partner.api.agent.factory.config;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.config.exception.ConfigNotExistException;
import work.slhaf.partner.api.agent.factory.config.exception.PromptNotExistException;
import work.slhaf.partner.api.agent.factory.config.pojo.ModelConfig;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.context.ConfigFactoryContext;
import work.slhaf.partner.api.agent.factory.module.ModuleCheckFactory;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigManager;
import work.slhaf.partner.api.agent.runtime.config.FileAgentConfigManager;
import work.slhaf.partner.api.chat.pojo.Message;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <h2>Agent启动流程 0</h2>
 * <p>
 * 通过指定的 {@link AgentConfigManager} 或者默认的 {@link FileAgentConfigManager} 加载配置文件
 * <p/>
 *
 * <p>下一步流程请参阅{@link ModuleCheckFactory}</p>
 */
@Slf4j
public class ConfigLoaderFactory extends AgentBaseFactory {

    private AgentConfigManager agentConfigManager;
    private HashMap<String, ModelConfig> modelConfigMap;
    private HashMap<String, List<Message>> modelPromptMap;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        ConfigFactoryContext factoryContext = context.getConfigFactoryContext();
        modelConfigMap = factoryContext.getModelConfigMap();
        modelPromptMap = factoryContext.getModelPromptMap();

        if (AgentConfigManager.INSTANCE == null) {
            AgentConfigManager.setINSTANCE(new FileAgentConfigManager());
        }

        agentConfigManager = AgentConfigManager.INSTANCE;
    }

    @Override
    protected void run() {
        agentConfigManager.load();
        modelConfigMap.putAll(agentConfigManager.getModelConfigMap());
        modelPromptMap.putAll(agentConfigManager.getModelPromptMap());
        check();
    }

    /**
     * 对模型Config与Prompt分别进行检验,除了都必须包含default外，还需要确保数量、key一致，毕竟是模型配置与提示词
     */
    private void check() {
        log.info("执行config与prompt检测...");
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
        //检查提示词数量与`ActivateModel`的实现数量是否一致
        log.info("检测完毕.");
    }
}
