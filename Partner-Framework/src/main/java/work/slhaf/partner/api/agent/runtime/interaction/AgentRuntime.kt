package work.slhaf.partner.api.agent.runtime.interaction

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule
import work.slhaf.partner.api.agent.factory.context.AgentContext
import work.slhaf.partner.api.agent.factory.context.ModuleContextData
import work.slhaf.partner.api.agent.runtime.interaction.flow.RunningFlowContext

object AgentRuntime {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val channel =
        Channel<TurnRequest<RunningFlowContext>>(Channel.UNLIMITED)

    @Volatile
    private var runningModules:
            Map<Int, List<ModuleContextData.Running<AbstractAgentModule.Running<RunningFlowContext>>>> =
        emptyMap()

    init {
        scope.launch {
            for (req in channel) {
                val result = executeTurn(req.context)
                req.deferred.complete(result)
            }
        }
    }

    fun refreshRunningModules() {
        runningModules = buildRunningModules()
    }

    fun <C : RunningFlowContext> submit(context: C): C = runBlocking {
        val deferred = CompletableDeferred<RunningFlowContext>()
        channel.send(TurnRequest(context, deferred))
        @Suppress("UNCHECKED_CAST")
        (return@runBlocking deferred.await() as C)
    }

    private suspend fun executeTurn(
        runningFlowContext: RunningFlowContext
    ): RunningFlowContext {

        if (runningModules.isEmpty()) {
            refreshRunningModules()
        }

        try {
            for (modules in runningModules.values) {
                executeOrder(modules, runningFlowContext)
            }
        } catch (e: Exception) {
            runningFlowContext.status.ok = false
            runningFlowContext.status.errMsg.add(e.localizedMessage)
        }

        return runningFlowContext
    }

    private suspend fun executeOrder(
        modules: List<ModuleContextData.Running<AbstractAgentModule.Running<RunningFlowContext>>>,
        runningFlowContext: RunningFlowContext
    ) {
        coroutineScope {
            val jobs = modules.map { module ->
                async {
                    module.instance.execute(runningFlowContext)
                }
            }
            jobs.awaitAll()
        }
    }

    private fun buildRunningModules():
            Map<Int, List<ModuleContextData.Running<AbstractAgentModule.Running<RunningFlowContext>>>> {

        return AgentContext.modules
            .values
            .filterIsInstance<ModuleContextData.Running<AbstractAgentModule.Running<RunningFlowContext>>>()
            .filter { it.enabled }
            .groupBy { it.order }
            .toSortedMap()
    }

    private data class TurnRequest<C : RunningFlowContext>(
        val context: C,
        val deferred: CompletableDeferred<RunningFlowContext>
    )
}