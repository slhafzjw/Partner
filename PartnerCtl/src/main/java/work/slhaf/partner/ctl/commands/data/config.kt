package work.slhaf.partner.ctl.commands.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GatewayConfig(
    val defaultChannel: String,
    val channels: List<ChannelConfig>
) {
    @Serializable
    data class ChannelConfig(
        val channelName: String,
        val params: JsonObject
    )
}

interface ProviderConfig {
    val name: String
    val type: String
    val defaultModel: String
}

@Serializable
data class OpenAiCompatible(
    override val name: String,
    override val defaultModel: String,

    val baseUrl: String,
    val apiKey: String,
) : ProviderConfig {
    override val type: String = "OPENAI_COMPATIBLE"
}