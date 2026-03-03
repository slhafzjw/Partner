package work.slhaf.partner.api.agent.runtime.interaction

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule
import work.slhaf.partner.api.agent.factory.context.AgentContext
import work.slhaf.partner.api.agent.factory.context.ModuleContextData
import work.slhaf.partner.api.agent.runtime.interaction.data.AgentInputData
import work.slhaf.partner.api.agent.runtime.interaction.data.AgentOutputData
import work.slhaf.partner.api.agent.runtime.interaction.flow.RunningFlowContext

abstract class AgentInteractionAdapter<I : AgentInputData, O : AgentOutputData, C : RunningFlowContext> {

    fun call(runningFlowContext: C): C = runBlocking {
        val runningModules =
            mutableMapOf<Int, MutableList<ModuleContextData.Running<AbstractAgentModule.Running<RunningFlowContext>>>>()

        AgentContext.modules
            .filter { ModuleContextData.Running::class.java.isAssignableFrom(it.value.javaClass) }
            .map { it.value as ModuleContextData.Running<AbstractAgentModule.Running<RunningFlowContext>> }
            .filter { it.enabled }
            .sortedBy { it.order }
            .forEach { runningModules.computeIfAbsent(it.order) { mutableListOf() }.add(it) }

        try {
            for (modules in runningModules.values) {
                executeOrder(modules, runningFlowContext)
            }
            runningFlowContext.ok = 1
        } catch (e: Exception) {
            runningFlowContext.ok = 0
            runningFlowContext.errMsg.add(e.localizedMessage)
        }

        return@runBlocking runningFlowContext
    }

    private suspend fun executeOrder(
        modules: MutableList<ModuleContextData.Running<AbstractAgentModule.Running<RunningFlowContext>>>,
        runningFlowContext: C
    ) {

        coroutineScope {

            val jobs = modules.map { module ->
                async {
                    module.instance.execute(runningFlowContext)
                }
            }

            jobs.awaitAll()   // 任一异常会取消全部
        }
    }

    protected abstract fun parseOutputData(outputContext: C): O

    protected abstract fun parseInputData(inputData: I): C
}
