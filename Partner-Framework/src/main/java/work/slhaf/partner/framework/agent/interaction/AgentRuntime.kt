package work.slhaf.partner.framework.agent.interaction

import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.annotation.JSONField
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import work.slhaf.partner.framework.agent.config.Config
import work.slhaf.partner.framework.agent.config.ConfigRegistration
import work.slhaf.partner.framework.agent.config.Configurable
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule
import work.slhaf.partner.framework.agent.factory.context.AgentContext
import work.slhaf.partner.framework.agent.factory.context.ModuleContextData
import work.slhaf.partner.framework.agent.interaction.data.InteractionEvent
import work.slhaf.partner.framework.agent.interaction.flow.RunningFlowContext
import java.nio.file.Path

object AgentRuntime : Configurable, ConfigRegistration<ModuleMaskConfig> {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val channel = Channel<RunningFlowContext>(Channel.UNLIMITED)
    private val responseChannels = mutableMapOf<String, ResponseChannel>(
        LogChannel.channelName to LogChannel
    )

    @Volatile
    private var defaultChannel: String = LogChannel.channelName

    @Volatile
    private var runningModules: Map<Int, List<AbstractAgentModule.Running<RunningFlowContext>>> = emptyMap()

    @Volatile
    private var maskedModules: Set<String> = emptySet()

    init {
        register()
        scope.launch {
            for (ctx in channel) {
                executeTurn(ctx)
            }
        }
    }

    fun registerResponseChannel(channelName: String, responseChannel: ResponseChannel) {
        responseChannels[channelName] = responseChannel
    }

    fun unregisterResponseChannel(channelName: String) {
        if (channelName == LogChannel.channelName) {
            return
        }
        responseChannels.remove(channelName)
    }

    fun setDefaultResponseChannel(channelName: String) {
        defaultChannel = channelName
    }

    fun defaultResponseChannel(): String = defaultChannel

    @JvmOverloads
    fun response(event: InteractionEvent, channelName: String = defaultChannel) {
        val channel = responseChannels[channelName]
        if (channel == null) {
            responseChannels[defaultChannel]?.response(event) ?: LogChannel.response(event)
        } else {
            channel.response(event)
        }
    }

    fun <C : RunningFlowContext> submit(context: C) = runBlocking {
        channel.send(context)
    }

    private suspend fun executeTurn(runningFlowContext: RunningFlowContext) {

        if (runningModules.isEmpty()) {
            refreshRunningModules()
        }

        for (modules in runningModules.values) {
            executeOrder(modules, runningFlowContext)
        }

    }

    private fun refreshRunningModules() {
        runningModules = AgentContext.modules.values
            .filterIsInstance<ModuleContextData.Running<AbstractAgentModule.Running<RunningFlowContext>>>()
            .filterNot { maskedModules.contains(it.instance.moduleName) }
            .groupBy { it.order }
            .mapValues { it.value.map { contextData -> contextData.instance } }
            .toSortedMap()
    }

    private suspend fun executeOrder(
        modules: List<AbstractAgentModule.Running<RunningFlowContext>>,
        runningFlowContext: RunningFlowContext
    ) {
        coroutineScope {
            val jobs = modules.map { module ->
                async {
                    if (runningFlowContext.skippedModules.contains(module.moduleName)) {
                        return@async
                    }
                    module.execute(runningFlowContext)
                }
            }
            jobs.awaitAll()
        }
    }

    override fun declare(): Map<Path, ConfigRegistration<out Config>> {
        return mapOf(Path.of("masked_modules.json") to this)
    }

    override fun type(): Class<ModuleMaskConfig> {
        return ModuleMaskConfig::class.java
    }

    override fun init(
        config: ModuleMaskConfig,
        json: JSONObject?
    ) {
        applyModuleMask(config)
    }

    override fun onReload(
        config: ModuleMaskConfig,
        json: JSONObject?
    ) {
        applyModuleMask(config)
    }

    override fun defaultConfig(): ModuleMaskConfig {
        return ModuleMaskConfig(setOf())
    }

    private fun applyModuleMask(config: ModuleMaskConfig) {
        maskedModules = config.maskedModules
        refreshRunningModules()
    }

}

data class ModuleMaskConfig(
    @field:JSONField(name = "masked_modules")
    val maskedModules: Set<String>
) : Config()
