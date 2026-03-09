package work.slhaf.partner.api.agent.factory.component.abstracts

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import work.slhaf.partner.api.agent.factory.component.annotation.AgentComponent
import work.slhaf.partner.api.agent.runtime.config.AgentConfigLoader
import work.slhaf.partner.api.agent.runtime.interaction.flow.RunningFlowContext
import work.slhaf.partner.api.chat.pojo.Message
import work.slhaf.partner.api.chat.runtime.OpenAiChatRuntime

/**
 * 模块基类
 */
@AgentComponent
sealed class AbstractAgentModule {

    var moduleName: String = javaClass.simpleName

    @JvmField
    val log: Logger = LoggerFactory.getLogger(javaClass.simpleName)

    abstract class Running<T : RunningFlowContext> : AbstractAgentModule() {

        abstract fun execute(context: T)

        abstract fun order(): Int
    }

    abstract class Sub<I, O> : AbstractAgentModule() {
        abstract fun execute(input: I): O
    }

    abstract class Standalone : AbstractAgentModule()

}

interface ActivateModel {

    val runtime: OpenAiChatRuntime
        get() = runtimeMap.computeIfAbsent(modelKey()) {
            buildRuntime()
        }

    companion object {
        val runtimeMap: MutableMap<String, OpenAiChatRuntime> = mutableMapOf()
        private val configManager: AgentConfigLoader = AgentConfigLoader.INSTANCE
    }

    fun buildRuntime(): OpenAiChatRuntime {
        val modelConfig = configManager.loadModelConfig(modelKey())
        return OpenAiChatRuntime(modelConfig.baseUrl, modelConfig.apikey, modelConfig.model)
    }

    fun chat(messages: List<Message>): String {
        return runtime.chat(mergeMessages(messages), useStreaming())
    }

    fun <T : Any> formattedChat(messages: List<Message>, responseType: Class<T>): T {
        return runtime.formattedChat(mergeMessages(messages), useStreaming(), responseType)
    }

    fun mergeMessages(messages: List<Message>): List<Message> {
        if (modulePrompt().isEmpty()) {
            return messages
        }
        return buildList {
            addAll(modulePrompt())
            addAll(messages)
        }
    }

    /**
     * 对应调用的模型配置名称
     */
    fun modelKey(): String {
        return if (this is AbstractAgentModule) {
            this.moduleName
        } else {
            javaClass.simpleName
        }
    }

    fun modulePrompt(): List<Message> = emptyList()

    fun useStreaming(): Boolean = false
}
