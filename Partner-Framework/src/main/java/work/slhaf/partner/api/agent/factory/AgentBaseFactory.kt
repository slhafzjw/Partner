package work.slhaf.partner.api.agent.factory

import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext

abstract class AgentBaseFactory {
    abstract fun execute(context: AgentRegisterContext)
}
