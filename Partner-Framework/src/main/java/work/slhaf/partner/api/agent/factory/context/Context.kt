package work.slhaf.partner.api.agent.factory.context

import com.alibaba.fastjson2.JSONArray
import work.slhaf.partner.api.agent.factory.module.abstracts.AbstractAgentModule
import java.time.ZonedDateTime

object AgentContext {
    val modules = mutableMapOf<String, ModuleContextData<AbstractAgentModule>>()
    val capabilities = mutableMapOf<Class<*>, Any>()
}

sealed class ModuleContextData<out T : AbstractAgentModule> {
    abstract val clazz: Class<out T>
    abstract val instance: T
    abstract val launchTime: ZonedDateTime

    val modelInfo: ModelInfo? = null
    val metadata = mutableMapOf<String, Any>()

    data class Running<T : AbstractAgentModule.Running<*>>(
        override val clazz: Class<T>,
        override val instance: T,
        override val launchTime: ZonedDateTime,

        val order: Int,
        val enabled: Boolean
    ) : ModuleContextData<T>()

    data class Sub<T : AbstractAgentModule.Sub<*, *>>(
        override val clazz: Class<T>,
        override val instance: T,
        override val launchTime: ZonedDateTime,

        val injectTarget: MutableSet<AbstractAgentModule> = mutableSetOf()
    ) : ModuleContextData<T>()

    data class Standalone<T : AbstractAgentModule.Standalone>(
        override val clazz: Class<T>,
        override val instance: T,
        override val launchTime: ZonedDateTime,

        val injectTarget: MutableSet<AbstractAgentModule> = mutableSetOf()
    ) : ModuleContextData<T>()

    data class ModelInfo(
        val baseUrl: String,
        val model: String,
        val basePrompt: JSONArray
    )
}