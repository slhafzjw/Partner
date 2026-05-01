package work.slhaf.partner.external.onebot.gateway

import work.slhaf.partner.framework.agent.interaction.AgentGateway
import work.slhaf.partner.framework.agent.interaction.AgentGatewayRegistration

class OnebotGatewayRegistration : AgentGatewayRegistration {
    override val channelName: String = "onebot_channel"

    override fun create(params: Map<String, String>): AgentGateway<*, *> {
        val port = params["port"]?.toIntOrNull() ?: 29700
        val hostname = params["hostname"] ?: "127.0.0.1"
        val path = params["path"] ?: "/onebot/v11/ws"
        val token = params["token"]

        require(port > 0) { "port must be greater than 0" }
        return OnebotGateway(port, hostname, path, token)
    }

}