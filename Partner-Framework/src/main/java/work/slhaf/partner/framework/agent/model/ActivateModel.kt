package work.slhaf.partner.framework.agent.model

import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule
import work.slhaf.partner.framework.agent.model.pojo.Message

interface ActivateModel {

    fun chat(messages: List<Message>): String {
        return ModelRuntimeRegistry.resolveProvider(modelKey()).chat(mergeMessages(messages))
    }

    fun streamChat(messages: List<Message>, handler: StreamChatMessageConsumer) {
        ModelRuntimeRegistry.resolveProvider(modelKey()).streamChat(mergeMessages(messages), handler)
    }

    fun <T : Any> formattedChat(messages: List<Message>, responseType: Class<T>): T {
        return ModelRuntimeRegistry.resolveProvider(modelKey()).formattedChat(mergeMessages(messages), responseType)
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
}