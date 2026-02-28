package work.slhaf.partner.api.agent.factory.config

import org.slf4j.LoggerFactory
import work.slhaf.partner.api.agent.factory.AgentBaseFactory
import work.slhaf.partner.api.agent.factory.config.exception.ConfigNotExistException
import work.slhaf.partner.api.agent.factory.config.exception.PromptNotExistException
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext
import work.slhaf.partner.api.agent.runtime.config.AgentConfigLoader
import work.slhaf.partner.api.agent.runtime.config.FileAgentConfigLoader
import java.lang.reflect.Modifier

/**
 * Agent 启动阶段的配置加载工厂。
 *
 * 行为:
 * - 使用全局 `AgentConfigLoader.INSTANCE`，为空时退回 [FileAgentConfigLoader]。
 * - 加载并写入 `modelConfigMap`、`modelPromptMap` 到 `ConfigFactoryContext`。
 * - 校验 `default` 配置与 `basic` 提示词是否存在。
 * - 反射读取配置加载器实现类（相对基类新增）的静态字段，并写入 `AgentContext.metadata`。
 */
class ConfigLoaderFactory : AgentBaseFactory() {

    companion object {
        private val log = LoggerFactory.getLogger(ConfigLoaderFactory::class.java)
    }

    override fun execute(context: AgentRegisterContext) {
        val agentConfigLoader = AgentConfigLoader.INSTANCE ?: FileAgentConfigLoader().also {
            AgentConfigLoader.INSTANCE = it
        }

        agentConfigLoader.load()

        val configFactoryContext = context.configFactoryContext
        configFactoryContext.modelConfigMap.putAll(agentConfigLoader.modelConfigMap)
        configFactoryContext.modelPromptMap.putAll(agentConfigLoader.modelPromptMap)

        check(configFactoryContext.modelConfigMap.keys, configFactoryContext.modelPromptMap.keys)
        collectLoaderMetadata(context, agentConfigLoader)
    }

    private fun check(configKeys: Set<String>, promptKeys: Set<String>) {
        log.info("执行config与prompt检测...")
        if (!configKeys.contains("default")) {
            throw ConfigNotExistException("缺少默认配置! 需确保存在一个模型配置的key为`default`")
        }
        if (!promptKeys.contains("basic")) {
            throw PromptNotExistException("缺少基础Prompt! 需要确保存在key为basic的Prompt文件，它将与其他Prompt共同作用于模块节点。")
        }

        val configKeySet = configKeys.toMutableSet().apply { remove("default") }
        val promptKeySet = promptKeys.toMutableSet().apply { remove("basic") }
        if (!promptKeySet.containsAll(configKeySet)) {
            log.warn("存在未被提示词包含的模型配置，该配置将无法生效!")
        }
        log.info("检测完毕.")
    }

    private fun collectLoaderMetadata(context: AgentRegisterContext, loader: AgentConfigLoader) {
        val fieldNamesInBaseType = AgentConfigLoader::class.java.declaredFields
            .asSequence()
            .filterNot { it.isSynthetic }
            .map { it.name }
            .toSet()

        val implementationType = loader::class.java
        implementationType.declaredFields
            .asSequence()
            .filterNot { it.isSynthetic }
            .filterNot { fieldNamesInBaseType.contains(it.name) }
            .filterNot { !Modifier.isStatic(it.modifiers) }
            .forEach { field ->
                field.isAccessible = true
                val value = field.get(loader)
                context.agentContext.addMetadata(field.name, value)
            }
    }
}
