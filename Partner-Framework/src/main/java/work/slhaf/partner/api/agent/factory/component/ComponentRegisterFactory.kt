package work.slhaf.partner.api.agent.factory.component

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import org.slf4j.LoggerFactory
import work.slhaf.partner.api.agent.factory.AgentBaseFactory
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule
import work.slhaf.partner.api.agent.factory.component.annotation.AgentComponent
import work.slhaf.partner.api.agent.factory.component.exception.ModuleFactoryInitFailedException
import work.slhaf.partner.api.agent.factory.config.pojo.ModelConfig
import work.slhaf.partner.api.agent.factory.context.AgentContext
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext
import work.slhaf.partner.api.agent.factory.context.ModuleContextData
import work.slhaf.partner.api.agent.model.ActivateModel
import java.lang.reflect.Modifier
import java.time.ZonedDateTime

/**
 * 扫描并实例化 `@AgentComponent` 具体类，写入 [AgentContext]。
 *
 * 行为:
 * - 若实例是 [AbstractAgentModule]，按 Running/Sub/Standalone 构造 `ModuleContextData` 并注册到 modules。
 * - 若实现了 [ActivateModel]，使用模块提供的 prompt 元数据构建 `modelInfo`。
 * - 若不是模块类型，尝试注册为 additional component（失败仅记录错误日志）。
 */
class ComponentRegisterFactory : AgentBaseFactory() {
    companion object {
        private val log = LoggerFactory.getLogger(ComponentRegisterFactory::class.java)
    }

    override fun execute(context: AgentRegisterContext) {
        val reflections = context.reflections
        val configFactoryContext = context.configFactoryContext
        val agentContext = context.agentContext

        val modelConfigMap = configFactoryContext.modelConfigMap
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
                        defaultConfig
                    )
                } else {
                    addAdditionalComponent(agentContext, componentClass, componentInstance)
                }
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerModule(
        agentContext: AgentContext,
        componentClass: Class<*>,
        module: AbstractAgentModule,
        modelConfigMap: Map<String, ModelConfig>,
        defaultConfig: ModelConfig
    ) {
        if (agentContext.modules.containsKey(module.moduleName)) {
            throw ModuleFactoryInitFailedException(
                "模块注册失败, 存在重复 moduleName: ${module.moduleName} (class=${componentClass.name})"
            )
        }

        val launchTime = ZonedDateTime.now()
        val modelInfo = if (module is ActivateModel) {
            val modelKey = module.modelKey()
            val modelConfig = modelConfigMap[modelKey] ?: defaultConfig
            ModuleContextData.ModelInfo(
                modelConfig.baseUrl,
                modelConfig.model,
                JSONArray.parseArray(JSONObject.toJSONString(module.modulePrompt()))
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
        agentContext: AgentContext,
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
