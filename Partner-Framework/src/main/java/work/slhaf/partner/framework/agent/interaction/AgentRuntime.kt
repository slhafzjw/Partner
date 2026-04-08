package work.slhaf.partner.framework.agent.interaction

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule
import work.slhaf.partner.framework.agent.factory.context.AgentContext
import work.slhaf.partner.framework.agent.factory.context.ModuleContextData
import work.slhaf.partner.framework.agent.interaction.data.InteractionEvent
import work.slhaf.partner.framework.agent.interaction.flow.RunningFlowContext

object AgentRuntime {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val channel = Channel<RunningFlowContext>(Channel.UNLIMITED)
    private val responseChannels = mutableMapOf<String, ResponseChannel>(
        LogChannel.channelName to LogChannel
    )

    @Volatile
    private var defaultChannel: String = LogChannel.channelName

    @Volatile
    private var runningModules: Map<Int, List<AbstractAgentModule.Running<RunningFlowContext>>> = emptyMap()

    init {
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
            .filter { it.enabled }
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

}
