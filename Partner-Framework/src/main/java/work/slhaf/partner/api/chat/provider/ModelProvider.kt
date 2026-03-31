package work.slhaf.partner.api.chat.provider

import com.alibaba.fastjson2.JSONObject
import work.slhaf.partner.api.chat.StreamChatMessageConsumer
import work.slhaf.partner.api.chat.pojo.Message

abstract class ModelProvider @JvmOverloads constructor(
    val override: ProviderOverride? = null
) {

    abstract fun fork(override: ProviderOverride): ModelProvider

    abstract fun streamChat(messages: List<Message>, consumer: StreamChatMessageConsumer)

    abstract fun chat(messages: List<Message>): String

    abstract fun <T> formattedChat(messages: List<Message>, type: Class<T>): T
}

data class ProviderOverride(
    val model: String,
    val temperature: Double?,
    val topP: Double?,
    val maxTokens: Int?,

    val extras: JSONObject?
)