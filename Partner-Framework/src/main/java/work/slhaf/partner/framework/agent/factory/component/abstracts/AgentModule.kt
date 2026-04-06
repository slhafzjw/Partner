package work.slhaf.partner.framework.agent.factory.component.abstracts

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import work.slhaf.partner.framework.agent.factory.component.annotation.AgentComponent
import work.slhaf.partner.framework.agent.interaction.flow.RunningFlowContext

/**
 * 模块基类
 */
@AgentComponent
sealed class AbstractAgentModule {

    var moduleName: String = javaClass.simpleName

    @JvmField
    val log: Logger = LoggerFactory.getLogger(javaClass.simpleName)

    abstract class Running<T : RunningFlowContext> : AbstractAgentModule() {

        abstract fun execute(context: T)

        abstract fun order(): Int
    }

    abstract class Sub<I, O> : AbstractAgentModule() {
        abstract fun execute(input: I): O
    }

    abstract class Standalone : AbstractAgentModule()

}

