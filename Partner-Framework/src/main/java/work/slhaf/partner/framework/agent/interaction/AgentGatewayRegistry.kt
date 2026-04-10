package work.slhaf.partner.framework.agent.interaction

import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.annotation.JSONField
import org.slf4j.LoggerFactory
import work.slhaf.partner.framework.agent.config.Config
import work.slhaf.partner.framework.agent.config.ConfigDoc
import work.slhaf.partner.framework.agent.config.ConfigRegistration
import work.slhaf.partner.framework.agent.config.Configurable
import work.slhaf.partner.framework.agent.exception.*
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object AgentGatewayRegistry : Configurable, ConfigRegistration<AgentGatewayRegistryConfig>, AutoCloseable {
    private const val COMPONENT_NAME = "agent-gateway-registry"

    private val log = LoggerFactory.getLogger(AgentGatewayRegistry::class.java)
    private val registryLock = ReentrantLock()
    private val registrations = linkedMapOf<String, AgentGatewayRegistration>()
    private val runningChannels = linkedMapOf<String, RunningGateway>()

    override fun declare(): Map<Path, ConfigRegistration<out Config>> {
        return mapOf(Path.of("gateway.json") to this)
    }

    override fun type(): Class<AgentGatewayRegistryConfig> = AgentGatewayRegistryConfig::class.java

    fun register(registration: AgentGatewayRegistration) = registryLock.withLock {
        val previous = registrations.putIfAbsent(registration.channelName, registration)
        checkAgentStartup(previous == null || previous === registration) {
            AgentStartupException(
                "AgentGateway channel already registered: ${registration.channelName}",
                COMPONENT_NAME
            )
        }
    }

    fun resolve(channelName: String): AgentGateway<*, *>? = registryLock.withLock {
        runningChannels[channelName]?.gateway
    }

    internal fun snapshotRunningChannels(): Map<String, AgentGateway<*, *>> = registryLock.withLock {
        runningChannels.mapValues { it.value.gateway }
    }

    override fun init(config: AgentGatewayRegistryConfig, json: JSONObject?) = registryLock.withLock {
        try {
            applyConfig(config)
        } catch (e: GatewayRegistryException) {
            throw GatewayStartupException(e.message ?: "Failed to apply gateway registry config", e.gatewayName, e)
        } catch (e: GatewayException) {
            throw GatewayStartupException(e.message ?: "Failed to apply gateway config", e.gatewayName, e)
        }
    }

    override fun onReload(config: AgentGatewayRegistryConfig, json: JSONObject?) = registryLock.withLock {
        val runtimeSnapshot = LinkedHashMap(runningChannels)
        val defaultSnapshot = AgentRuntime.defaultResponseChannel()
        try {
            applyConfig(config)
        } catch (e: GatewayRegistryException) {
            log.error("Error while reloading gateway config", e)
            restoreSnapshot(runtimeSnapshot, defaultSnapshot)
        } catch (e: GatewayException) {
            log.error("Error while reloading gateway config", e)
            restoreSnapshot(runtimeSnapshot, defaultSnapshot)
        }
    }

    override fun defaultConfig(): AgentGatewayRegistryConfig? = null

    override fun close() = registryLock.withLock {
        val currentChannels = runningChannels.keys.toList()
        currentChannels.forEach(this::stopChannel)
        AgentRuntime.setDefaultResponseChannel(LogChannel.channelName)
    }

    private fun applyConfig(config: AgentGatewayRegistryConfig) {
        validateConfig(config)
        reconcileChannels(config.channels)
        AgentRuntime.setDefaultResponseChannel(config.defaultChannel)
    }

    private fun validateConfig(config: AgentGatewayRegistryConfig) {
        if (config.defaultChannel.isBlank()) {
            throw GatewayRegistryException("default_channel must not be blank", COMPONENT_NAME)
        }
        if (config.channels.isEmpty()) {
            throw GatewayRegistryException("channels must not be empty", COMPONENT_NAME)
        }

        val channelNames = mutableSetOf<String>()
        config.channels.forEach { channel ->
            if (channel.channelName.isBlank()) {
                throw GatewayRegistryException("channel_name must not be blank", COMPONENT_NAME)
            }
            if (!channelNames.add(channel.channelName)) {
                throw GatewayRegistryException("Duplicate channel_name: ${channel.channelName}", channel.channelName)
            }
            if (!registrations.containsKey(channel.channelName)) {
                throw GatewayRegistryException(
                    "AgentGateway channel is not registered: ${channel.channelName}",
                    channel.channelName
                )
            }
        }

        if (!channelNames.contains(config.defaultChannel)) {
            throw GatewayRegistryException(
                "default_channel must exist in channels: ${config.defaultChannel}",
                config.defaultChannel
            )
        }
    }

    private fun reconcileChannels(configuredChannels: List<AgentGatewayChannelConfig>) {
        val expectedNames = configuredChannels.map { it.channelName }.toSet()
        val removedNames = runningChannels.keys.filterNot(expectedNames::contains)
        removedNames.forEach(this::stopChannel)

        configuredChannels.forEach { channelConfig ->
            val registration = registrations[channelConfig.channelName]
                ?: throw GatewayRegistryException(
                    "AgentGateway channel is not registered: ${channelConfig.channelName}",
                    channelConfig.channelName
                )
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
            throw GatewayException(
                "Failed to launch gateway channel: ${channelConfig.channelName}",
                channelConfig.channelName,
                e
            )
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
