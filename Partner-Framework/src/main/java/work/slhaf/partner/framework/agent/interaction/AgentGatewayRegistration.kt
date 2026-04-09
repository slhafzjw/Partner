package work.slhaf.partner.framework.agent.interaction

interface AgentGatewayRegistration {

    val channelName: String

    fun create(params: Map<String, String>): AgentGateway<*, *>

    fun supportsHotReloadReuse(oldParams: Map<String, String>, newParams: Map<String, String>): Boolean {
        return oldParams == newParams
    }

    fun shutdown(instance: AgentGateway<*, *>) {
        instance.close()
    }

    fun register() {
        AgentGatewayRegistry.register(this)
    }
}
