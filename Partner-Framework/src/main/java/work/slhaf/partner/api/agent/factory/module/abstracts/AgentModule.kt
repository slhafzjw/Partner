package work.slhaf.partner.api.agent.factory.module.abstracts

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
abstract class AbstractAgentModule {
    var moduleName: String = javaClass.simpleName
}

data class Model(
    val chatClient: ChatClient,
    val chatMessages: MutableList<Message> = mutableListOf(),
    val baseMessages: MutableList<Message> = mutableListOf()
)

interface RunningModule<T : RunningFlowContext> {
    fun execute(context: T)
}

interface SubModule<I, O> {
    fun execute(input: I): O
}

interface StandaloneModule

interface ActivateModel {

    companion object {
        val configManager: AgentConfigManager = AgentConfigManager.INSTANCE
        val modelMap: MutableMap<String, Model> = mutableMapOf()
    }

    fun getModel(): Model {
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

        val model = modelMap.computeIfAbsent(modelKey()) {
            buildModel()
        }
        return model
    }

    @Init(order = -1)
    fun modelSettings() {
        val model = getModel()
        modelMap[modelKey()] = model
    }

    private fun loadSpecificPromptAndBasicPrompt(modelKey: String): MutableList<Message> {
        val messages: MutableList<Message> = ArrayList()
        messages.addAll(configManager.loadModelPrompt("basic"))
        messages.addAll(configManager.loadModelPrompt(modelKey))
        return messages
    }

    fun chat(): ChatResponse {
        val model = this.getModel()
        val temp = ArrayList<Message?>()
        temp.addAll(model.baseMessages)
        temp.addAll(model.chatMessages)
        return model.chatClient.runChat(temp)
    }

    fun singleChat(input: String): ChatResponse {
        val model = this.getModel()
        val temp = ArrayList<Message>(model.baseMessages)
        temp.add(Message(ChatConstant.Character.USER, input))
        return model.chatClient.runChat(temp)
    }

    fun updateChatClientSettings() {
        val model = this.getModel()
        model.chatClient.temperature = 0.4
        model.chatClient.top_p = 0.8
    }

    /**
     * 对应调用的模型配置名称
     */
    fun modelKey(): String {
        return (this as AbstractAgentModule).moduleName
    }

    fun withBasicPrompt(): Boolean

}
