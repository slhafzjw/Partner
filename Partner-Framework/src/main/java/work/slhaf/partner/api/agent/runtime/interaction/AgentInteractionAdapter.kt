package work.slhaf.partner.api.agent.runtime.interaction

import work.slhaf.partner.api.agent.runtime.interaction.data.AgentInputData
import work.slhaf.partner.api.agent.runtime.interaction.data.AgentOutputData
import work.slhaf.partner.api.agent.runtime.interaction.flow.RunningFlowContext

abstract class AgentInteractionAdapter<
        I : AgentInputData,
        O : AgentOutputData,
        C : RunningFlowContext
        > {

    protected val runtime: AgentRuntime = AgentRuntime // 由 AgentContext 持有实例

    fun submit(inputData: I): O {
        val ctx = parseInputData(inputData)

        val result = runtime.submit(ctx)

        return parseOutputData(result)
    }

    protected abstract fun parseOutputData(outputContext: C): O

    protected abstract fun parseInputData(inputData: I): C
}