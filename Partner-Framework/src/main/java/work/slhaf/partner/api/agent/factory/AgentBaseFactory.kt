package work.slhaf.partner.api.agent.factory

import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext

/**
 * 所有注册链工厂的统一抽象。
 *
 * 每个工厂接收同一个 [AgentRegisterContext]，
 * 在其上完成单一阶段的扫描、校验、构建或注入。
 */
abstract class AgentBaseFactory {
    abstract fun execute(context: AgentRegisterContext)
}
