package work.slhaf.partner.framework.agent.interaction

import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.annotation.JSONField
import org.slf4j.LoggerFactory
import work.slhaf.partner.framework.agent.config.Config
import work.slhaf.partner.framework.agent.config.ConfigDoc
import work.slhaf.partner.framework.agent.config.ConfigRegistration
import work.slhaf.partner.framework.agent.config.Configurable
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object AgentGatewayRegistry : Configurable, ConfigRegistration<AgentGatewayRegistryConfig> {

    private val log = LoggerFactory.getLogger(AgentGatewayRegistry::class.java)
    private val registryLock = ReentrantLock()
    private val registrations = linkedMapOf<String, AgentGatewayRegistration>()
    private val runningChannels = linkedMapOf<String, RunningGateway>()

    override fun declare(): Map<Path, ConfigRegistration<out Config>> {
        return mapOf(Path.of("gateway", "gateway.json") to this)
    }

    override fun type(): Class<AgentGatewayRegistryConfig> = AgentGatewayRegistryConfig::class.java

    fun register(registration: AgentGatewayRegistration) = registryLock.withLock {
        val previous = registrations.putIfAbsent(registration.channelName, registration)
        check(previous == null || previous === registration) {
            "AgentGateway channel already registered: ${registration.channelName}"
        }
    }

    fun resolve(channelName: String): AgentGateway<*, *>? = registryLock.withLock {
        runningChannels[channelName]?.gateway
    }

    internal fun snapshotRunningChannels(): Map<String, AgentGateway<*, *>> = registryLock.withLock {
        runningChannels.mapValues { it.value.gateway }
    }

    override fun init(config: AgentGatewayRegistryConfig, json: JSONObject?) = registryLock.withLock {
        applyConfig(config)
    }

    override fun onReload(config: AgentGatewayRegistryConfig, json: JSONObject?) = registryLock.withLock {
        val runtimeSnapshot = LinkedHashMap(runningChannels)
        val defaultSnapshot = AgentRuntime.defaultResponseChannel()
        try {
            applyConfig(config)
        } catch (e: Exception) {
            log.error("Error while reloading gateway config", e)
            restoreSnapshot(runtimeSnapshot, defaultSnapshot)
        }
    }

    override fun defaultConfig(): AgentGatewayRegistryConfig? = null

    private fun applyConfig(config: AgentGatewayRegistryConfig) {
        validateConfig(config)
        reconcileChannels(config.channels)
        AgentRuntime.setDefaultResponseChannel(config.defaultChannel)
    }

    private fun validateConfig(config: AgentGatewayRegistryConfig) {
        require(config.defaultChannel.isNotBlank()) { "default_channel must not be blank" }
        require(config.channels.isNotEmpty()) { "channels must not be empty" }

        val channelNames = mutableSetOf<String>()
        config.channels.forEach { channel ->
            require(channel.channelName.isNotBlank()) { "channel_name must not be blank" }
            require(channelNames.add(channel.channelName)) { "Duplicated channel_name: ${channel.channelName}" }
            require(registrations.containsKey(channel.channelName)) {
                "AgentGateway channel is not registered: ${channel.channelName}"
            }
        }

        require(channelNames.contains(config.defaultChannel)) {
            "default_channel must exist in channels: ${config.defaultChannel}"
        }
    }

    private fun reconcileChannels(configuredChannels: List<AgentGatewayChannelConfig>) {
        val expectedNames = configuredChannels.map { it.channelName }.toSet()
        val removedNames = runningChannels.keys.filterNot(expectedNames::contains)
        removedNames.forEach(this::stopChannel)

        configuredChannels.forEach { channelConfig ->
            val registration = registrations[channelConfig.channelName]
                ?: error("AgentGateway channel is not registered: ${channelConfig.channelName}")
            val existing = runningChannels[channelConfig.channelName]
            if (existing != null && existing.registration === registration &&
                registration.supportsHotReloadReuse(existing.params, channelConfig.params)
            ) {
                return@forEach
            }
            if (existing != null) {
                stopChannel(channelConfig.channelName)
            }
            startChannel(registration, channelConfig)
        }
    }

    private fun startChannel(
        registration: AgentGatewayRegistration,
        channelConfig: AgentGatewayChannelConfig
    ) {
        val gateway = registration.create(channelConfig.params)
        try {
            gateway.launch()
            AgentRuntime.registerResponseChannel(channelConfig.channelName, gateway)
            runningChannels[channelConfig.channelName] = RunningGateway(
                registration = registration,
                params = LinkedHashMap(channelConfig.params),
                gateway = gateway
            )
        } catch (e: Exception) {
            runCatching { registration.shutdown(gateway) }
                .onFailure { shutdownError ->
                    log.warn(
                        "Failed to shutdown gateway after launch failure: {}",
                        channelConfig.channelName,
                        shutdownError
                    )
                }
            throw e
        }
    }

    private fun stopChannel(channelName: String) {
        val running = runningChannels.remove(channelName) ?: return
        runCatching { running.registration.shutdown(running.gateway) }
            .onFailure { e -> log.warn("Failed to shutdown gateway: {}", channelName, e) }
        AgentRuntime.unregisterResponseChannel(channelName)
    }

    private fun restoreSnapshot(
        runtimeSnapshot: Map<String, RunningGateway>,
        defaultSnapshot: String
    ) {
        val currentChannels = runningChannels.keys.toList()
        currentChannels.forEach(this::stopChannel)

        runtimeSnapshot.forEach { (channelName, running) ->
            AgentRuntime.registerResponseChannel(channelName, running.gateway)
            runningChannels[channelName] = running
        }
        AgentRuntime.setDefaultResponseChannel(defaultSnapshot)
    }

    private data class RunningGateway(
        val registration: AgentGatewayRegistration,
        val params: Map<String, String>,
        val gateway: AgentGateway<*, *>
    )
}

data class AgentGatewayRegistryConfig(
    @field:JSONField(name = "default_channel")
    @field:ConfigDoc(description = "默认响应通道", example = "websocket_channel")
    val defaultChannel: String,
    @field:ConfigDoc(
        description = "要启用的通道列表",
        example = """[
            {
              "channel_name": "websocket_channel",
              "params": {
                "port": "29600",
                "heartbeat_interval": "10000"
              }
            }
        ]"""
    )
    val channels: List<AgentGatewayChannelConfig>
) : Config()

data class AgentGatewayChannelConfig(
    @field:JSONField(name = "channel_name")
    @field:ConfigDoc(description = "通道名称，同时对应已注册的 gateway 名称", example = "websocket_channel")
    val channelName: String,
    @field:ConfigDoc(description = "通道参数", example = """{ "key1": "value1", "key2": "value2" }""")
    val params: Map<String, String> = emptyMap()
)
