package work.slhaf.partner.api.agent.factory.context

import com.alibaba.fastjson2.JSONArray
import work.slhaf.partner.api.agent.factory.AgentComponent
import work.slhaf.partner.api.agent.factory.module.abstracts.AbstractAgentModule
import java.time.ZonedDateTime

object AgentContext {

    private val _modules =
        mutableMapOf<String, ModuleContextData<AbstractAgentModule>>()

    val modules: Map<String, ModuleContextData<AbstractAgentModule>>
        get() = _modules

    private val _capabilities =
        mutableMapOf<Class<*>, Any?>()

    val capabilities: Map<Class<*>, Any?>
        get() = _capabilities

    private val _additionalComponents = mutableSetOf<Any>()

    val additionalComponents: Set<Any>
        get() = _additionalComponents

    val metadata: MutableMap<String, Any> = mutableMapOf()

    fun addModule(name: String, module: ModuleContextData<AbstractAgentModule>) {
        _modules[name] = module
    }

    fun <T> addCapability(type: Class<T>, value: T) {
        _capabilities[type] = value
    }

    fun <T> addAdditionalComponent(type: Class<T>, value: T): Boolean {
        if (type.isAnnotationPresent(AgentComponent::class.java)) {
            return false
        }
        if (value == null) {
            return false
        }
        _additionalComponents.add(value)
        return true
    }
}

sealed class ModuleContextData<out T : AbstractAgentModule> {
    abstract val clazz: Class<out T>
    abstract val instance: T
    abstract val launchTime: ZonedDateTime
    abstract val modelInfo: ModelInfo

    val metadata = mutableMapOf<String, Any>()

    data class Running<T : AbstractAgentModule.Running<*>>(
        override val clazz: Class<T>,
        override val instance: T,
        override val launchTime: ZonedDateTime,
        override val modelInfo: ModelInfo,

        val order: Int,
        val enabled: Boolean
    ) : ModuleContextData<T>()

    data class Sub<T : AbstractAgentModule.Sub<*, *>>(
        override val clazz: Class<T>,
        override val instance: T,
        override val launchTime: ZonedDateTime,
        override val modelInfo: ModelInfo,

        val injectTarget: MutableSet<AbstractAgentModule> = mutableSetOf()
    ) : ModuleContextData<T>()

    data class Standalone<T : AbstractAgentModule.Standalone>(
        override val clazz: Class<T>,
        override val instance: T,
        override val launchTime: ZonedDateTime,
        override val modelInfo: ModelInfo,

        val injectTarget: MutableSet<AbstractAgentModule> = mutableSetOf()
    ) : ModuleContextData<T>()

    data class ModelInfo(
        val baseUrl: String,
        val model: String,
        val basePrompt: JSONArray
    )
}