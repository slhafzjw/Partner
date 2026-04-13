package work.slhaf.partner.framework.agent.factory.component.abstracts

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import work.slhaf.partner.framework.agent.factory.component.annotation.AgentComponent
import work.slhaf.partner.framework.agent.interaction.flow.RunningFlowContext
import work.slhaf.partner.framework.agent.log.LogAdviceProvider
import java.lang.reflect.ParameterizedType

/**
 * 模块基类
 */
@AgentComponent
sealed class AbstractAgentModule {

    var moduleName: String = javaClass.simpleName

    @JvmField
    val log: Logger = LoggerFactory.getLogger(javaClass.simpleName)

    abstract class Running<T : RunningFlowContext> : AbstractAgentModule() {

        private val advice = run {
            @Suppress("UNCHECKED_CAST")
            LogAdviceProvider.createAdvice(
                moduleName,
                resolveGenericType(0) as Class<T>,
                Void::class.java
            ) { context ->
                doExecute(context)
                null
            }
        }

        fun execute(context: T) {
            advice.invoke(context)
        }

        protected abstract fun doExecute(context: T)

        abstract fun order(): Int
    }

    abstract class Sub<I, O> : AbstractAgentModule() {
        private val advice = run {
            @Suppress("UNCHECKED_CAST")
            LogAdviceProvider.createAdvice(
                moduleName,
                resolveGenericType(0) as Class<I>,
                resolveGenericType(1) as Class<O>
            ) { input ->
                doExecute(input)
            }
        }

        fun execute(input: I): O? {
            return advice.invoke(input).getOrThrow()
        }

        protected abstract fun doExecute(input: I): O?
    }

    abstract class Standalone : AbstractAgentModule()

    protected fun resolveGenericType(index: Int): Class<*> {
        val genericType = (javaClass.genericSuperclass as? ParameterizedType)
            ?.actualTypeArguments
            ?.getOrNull(index)
            ?: return Any::class.java
        return when (genericType) {
            is Class<*> -> genericType
            is ParameterizedType -> genericType.rawType as? Class<*> ?: Any::class.java
            else -> Any::class.java
        }
    }
}
