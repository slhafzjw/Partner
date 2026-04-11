package work.slhaf.partner.framework.agent.model.provider

import com.alibaba.fastjson2.JSONObject
import work.slhaf.partner.framework.agent.model.StreamChatMessageConsumer
import work.slhaf.partner.framework.agent.model.pojo.Message
import work.slhaf.partner.framework.agent.support.Result

abstract class ModelProvider @JvmOverloads constructor(
    val providerName: String,
    val modelKey: String,
    val override: ProviderOverride? = null
) {

    abstract fun fork(modelKey: String, override: ProviderOverride? = null): ModelProvider

    abstract fun streamChat(messages: List<Message>, consumer: StreamChatMessageConsumer): Result<Unit>

    abstract fun chat(messages: List<Message>): Result<String>

    abstract fun <T> formattedChat(messages: List<Message>, type: Class<T>): Result<T>
}

data class ProviderOverride(
    val model: String,
    val temperature: Double?,
    val topP: Double?,
    val maxTokens: Int?,

    val extras: JSONObject?
)
