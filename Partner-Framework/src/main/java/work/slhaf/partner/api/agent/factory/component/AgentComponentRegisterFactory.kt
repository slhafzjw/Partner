package work.slhaf.partner.api.agent.factory.component

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import org.slf4j.LoggerFactory
import work.slhaf.partner.api.agent.factory.AgentBaseFactory
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule
import work.slhaf.partner.api.agent.factory.component.abstracts.ActivateModel
import work.slhaf.partner.api.agent.factory.component.annotation.AgentComponent
import work.slhaf.partner.api.agent.factory.component.exception.ModuleFactoryInitFailedException
import work.slhaf.partner.api.agent.factory.config.exception.PromptNotExistException
import work.slhaf.partner.api.agent.factory.config.pojo.ModelConfig
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext
import work.slhaf.partner.api.agent.factory.context.ModuleContextData
import work.slhaf.partner.api.chat.pojo.Message
import java.lang.reflect.Modifier
import java.time.ZonedDateTime

class AgentComponentRegisterFactory : AgentBaseFactory() {
    companion object {
        private val log = LoggerFactory.getLogger(AgentComponentRegisterFactory::class.java)
    }

    override fun execute(context: AgentRegisterContext) {
        val reflections = context.reflections
        val configFactoryContext = context.configFactoryContext
        val agentContext = context.agentContext

        val modelConfigMap = configFactoryContext.modelConfigMap
        val modelPromptMap = configFactoryContext.modelPromptMap
        val defaultConfig = modelConfigMap["default"]!!

        reflections.getTypesAnnotatedWith(AgentComponent::class.java)
            .asSequence()
            .filter { isConcreteClass(it) }
            .forEach { componentClass ->
                val componentInstance = try {
                    val constructor = componentClass.getDeclaredConstructor()
                    constructor.isAccessible = true
                    constructor.newInstance()
                } catch (e: Exception) {
                    throw ModuleFactoryInitFailedException("AgentComponent 实例化失败: ${componentClass.name}", e)
                }

                if (componentInstance is AbstractAgentModule) {
                    registerModule(
                        agentContext,
                        componentClass,
                        componentInstance,
                        modelConfigMap,
                        modelPromptMap,
                        defaultConfig
                    )
                } else {
                    addAdditionalComponent(agentContext, componentClass, componentInstance)
                }
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerModule(
        agentContext: work.slhaf.partner.api.agent.factory.context.AgentContext,
        componentClass: Class<*>,
        module: AbstractAgentModule,
        modelConfigMap: Map<String, ModelConfig>,
        modelPromptMap: Map<String, List<Message>>,
        defaultConfig: ModelConfig
    ) {
        val launchTime = ZonedDateTime.now()
        val modelInfo = if (module is ActivateModel) {
            val modelKey = module.modelKey()
            val modelConfig = modelConfigMap[modelKey] ?: defaultConfig
            val modelPrompt = modelPromptMap[modelKey]
                ?: throw PromptNotExistException("不存在的modelPrompt: $modelKey")
            ModuleContextData.ModelInfo(
                modelConfig.baseUrl,
                modelConfig.model,
                JSONArray.parseArray(JSONObject.toJSONString(modelPrompt))
            )
        } else {
            null
        }

        when (module) {
            is AbstractAgentModule.Running<*> -> {
                val moduleContext = ModuleContextData.Running(
                    clazz = componentClass as Class<AbstractAgentModule.Running<*>>,
                    instance = module,
                    launchTime = launchTime,
                    modelInfo = modelInfo,
                    order = module.order(),
                    enabled = true
                )
                agentContext.addModule(module.moduleName, moduleContext as ModuleContextData<AbstractAgentModule>)
            }

            is AbstractAgentModule.Sub<*, *> -> {
                val moduleContext = ModuleContextData.Sub(
                    clazz = componentClass as Class<AbstractAgentModule.Sub<*, *>>,
                    instance = module,
                    launchTime = launchTime,
                    modelInfo = modelInfo
                )
                agentContext.addModule(module.moduleName, moduleContext as ModuleContextData<AbstractAgentModule>)
            }

            is AbstractAgentModule.Standalone -> {
                val moduleContext = ModuleContextData.Standalone(
                    clazz = componentClass as Class<AbstractAgentModule.Standalone>,
                    instance = module,
                    launchTime = launchTime,
                    modelInfo = modelInfo
                )
                agentContext.addModule(module.moduleName, moduleContext as ModuleContextData<AbstractAgentModule>)
            }
        }
    }

    private fun addAdditionalComponent(
        agentContext: work.slhaf.partner.api.agent.factory.context.AgentContext,
        componentClass: Class<*>,
        componentInstance: Any
    ) {
        val added = agentContext.addAdditionalComponent(componentInstance)
        if (!added) {
            log.error("AgentComponent追加注册失败(被拒绝): {}", componentClass.name)
        }
    }

    private fun isConcreteClass(type: Class<*>): Boolean {
        if (type.isInterface || type.isAnnotation || type.isEnum || type.isArray || type.isPrimitive) {
            return false
        }
        if (type.isSynthetic || type.isAnonymousClass || type.isLocalClass) {
            return false
        }
        return !Modifier.isAbstract(type.modifiers)
    }
}
