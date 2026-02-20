package work.slhaf.partner.api.agent.factory.module.abstracts

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityHolder
import work.slhaf.partner.api.agent.factory.module.annotation.Init
import work.slhaf.partner.api.agent.runtime.config.AgentConfigManager
import work.slhaf.partner.api.agent.runtime.interaction.flow.entity.RunningFlowContext
import work.slhaf.partner.api.chat.ChatClient
import work.slhaf.partner.api.chat.constant.ChatConstant
import work.slhaf.partner.api.chat.pojo.ChatResponse
import work.slhaf.partner.api.chat.pojo.Message

/**
 * 模块基类
 */
@CapabilityHolder
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

    abstract class Standalone

    // TODO 后续于此处扩展生命周期内容
}

interface ActivateModel {

    val model: Model
        get() = modelMap.computeIfAbsent(modelKey()) {
            buildModel()
        }

    companion object {
        val modelMap: MutableMap<String, Model> = mutableMapOf()
        private val configManager: AgentConfigManager = AgentConfigManager.INSTANCE
    }

    @Init(order = -1)
    fun modelSettings() {
        modelMap[modelKey()] = buildModel()
    }

    fun buildModel(): Model {
        val modelConfig = configManager.loadModelConfig(modelKey())
        val chatClient = ChatClient(modelConfig.baseUrl, modelConfig.apikey, modelConfig.model)
        val model = Model(chatClient)

        val baseMessages = if (withBasicPrompt()) {
            loadSpecificPromptAndBasicPrompt(modelKey())
        } else {
            configManager.loadModelPrompt(modelKey())
        }
        model.baseMessages.addAll(baseMessages)
        return model
    }

    private fun loadSpecificPromptAndBasicPrompt(modelKey: String): MutableList<Message> {
        val messages: MutableList<Message> = ArrayList()
        messages.addAll(configManager.loadModelPrompt("basic"))
        messages.addAll(configManager.loadModelPrompt(modelKey))
        return messages
    }

    fun chat(): ChatResponse {
        val temp = ArrayList<Message?>()
        temp.addAll(model.baseMessages)
        temp.addAll(model.chatMessages)
        return model.chatClient.runChat(temp)
    }

    fun singleChat(input: String): ChatResponse {
        val temp = ArrayList<Message>(model.baseMessages)
        temp.add(Message(ChatConstant.Character.USER, input))
        return model.chatClient.runChat(temp)
    }

    fun updateChatClientSettings() {
        model.chatClient.temperature = 0.4
        model.chatClient.top_p = 0.8
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

    fun withBasicPrompt(): Boolean

    data class Model(
        val chatClient: ChatClient,
        val chatMessages: MutableList<Message> = mutableListOf(),
        val baseMessages: MutableList<Message> = mutableListOf()
    )
}
