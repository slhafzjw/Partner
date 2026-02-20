package work.slhaf.partner.api.agent.factory.context

import com.alibaba.fastjson2.JSONArray
import work.slhaf.partner.api.agent.factory.module.abstracts.AbstractAgentModule

object AgentContext {

}

sealed class ModuleContextData<T : AbstractAgentModule> {
    abstract val name: String
    abstract val clazz: Class<T>
    abstract val instance: T
    abstract val prompt: JSONArray
    abstract val modelActivated: Boolean

    data class RunningModule<T : AbstractAgentModule>(
        override val name: String,
        override val clazz: Class<T>,
        override val instance: T,
        override val prompt: JSONArray,
        override val modelActivated: Boolean,

        val order: Int
    ) : ModuleContextData<T>()

}