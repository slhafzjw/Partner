package work.slhaf.partner.runtime.interaction

import work.slhaf.partner.framework.agent.interaction.AgentGateway
import work.slhaf.partner.framework.agent.interaction.AgentGatewayRegistration

object WebSocketGatewayRegistration : AgentGatewayRegistration {

    override val channelName: String = "websocket_channel"

    override fun create(params: Map<String, String>): AgentGateway<*, *> {
        val port = params["port"]?.toIntOrNull() ?: 29600
        val heartbeatInterval = params["heartbeat_interval"]?.toLongOrNull() ?: 10_000L
        require(port > 0) { "port must be greater than 0" }
        require(heartbeatInterval > 0) { "heartbeat_interval must be greater than 0" }
        return WebSocketGateway(port, heartbeatInterval)
    }

    override fun shutdown(instance: AgentGateway<*, *>) {
        if (instance is WebSocketGateway) {
            instance.close()
        } else {
            super.shutdown(instance)
        }
    }
}
