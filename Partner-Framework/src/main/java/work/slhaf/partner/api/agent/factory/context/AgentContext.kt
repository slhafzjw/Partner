package work.slhaf.partner.api.agent.factory.context

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule
import work.slhaf.partner.api.agent.factory.component.annotation.AgentComponent
import java.lang.reflect.Method
import java.time.ZonedDateTime

object AgentContext {

    private val _modules =
        mutableMapOf<String, ModuleContextData<AbstractAgentModule>>()

    val modules: Map<String, ModuleContextData<AbstractAgentModule>>
        get() = _modules

    private val _capabilities =
        mutableMapOf<String, CapabilityImplementation>()

    val capabilities: Map<String, CapabilityImplementation>
        get() = _capabilities

    private val _additionalComponents = mutableMapOf<Class<*>, Any>()

    val additionalComponents: Map<Class<*>, Any>
        get() = _additionalComponents

    private val _metadata: MutableMap<String, MetaDataContent> = mutableMapOf()

    val metadata: Map<String, MetaDataContent>
        get() = _metadata

    fun addModule(name: String, module: ModuleContextData<AbstractAgentModule>) {
        _modules[name] = module
    }

    fun addCapability(capability: String, instance: Any, methods: Map<String, Method>) {
        val newImpl = CapabilityImplementation(instance.javaClass, instance, methods)
        _capabilities[capability] = newImpl
    }

    fun addAdditionalComponent(instance: Any): Boolean {
        val type = instance::class.java
        if (type.isAnnotationPresent(AgentComponent::class.java)) {
            return false
        }
        _additionalComponents[type] = instance
        return true
    }

    fun addMetadata(name: String, value: Any) {
        val content = MetaDataContent(value::class.java, JSONObject.toJSONString(value))
        _metadata[name] = content
    }

    data class MetaDataContent(
        val clazz: Class<*>,
        val value: String
    )

    data class CapabilityImplementation(
        val clazz: Class<*>,
        val instance: Any,
        val methods: Map<String, Method>
    )
}

sealed class ModuleContextData<out T : AbstractAgentModule> {
    abstract val clazz: Class<out T>
    abstract val instance: T
    abstract val launchTime: ZonedDateTime
    abstract val modelInfo: ModelInfo?

    val metadata = mutableMapOf<String, Any>()

    data class Running<T : AbstractAgentModule.Running<*>>(
        override val clazz: Class<T>,
        override val instance: T,
        override val launchTime: ZonedDateTime,
        override val modelInfo: ModelInfo?,

        val order: Int,
        val enabled: Boolean
    ) : ModuleContextData<T>()

    data class Sub<T : AbstractAgentModule.Sub<*, *>>(
        override val clazz: Class<T>,
        override val instance: T,
        override val launchTime: ZonedDateTime,
        override val modelInfo: ModelInfo?,

        val injectTarget: MutableSet<AbstractAgentModule> = mutableSetOf()
    ) : ModuleContextData<T>()

    data class Standalone<T : AbstractAgentModule.Standalone>(
        override val clazz: Class<T>,
        override val instance: T,
        override val launchTime: ZonedDateTime,
        override val modelInfo: ModelInfo?,

        val injectTarget: MutableSet<AbstractAgentModule> = mutableSetOf()
    ) : ModuleContextData<T>()

    data class ModelInfo(
        val baseUrl: String,
        val model: String,
        val basePrompt: JSONArray
    )
}

/**
 * # Shutdown Hook 注解
 * - 可用于[AgentComponent]相关类、[work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore]相关类。
 * - 关闭时将按照：Running -> Additional -> Standalone -> Sub -> Capability 的顺序执行
 * - [order] 仅在同一层级内起顺序对比作用，数值越小，执行越早。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Shutdown(
    val order: Int = 0,
)
