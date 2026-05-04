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