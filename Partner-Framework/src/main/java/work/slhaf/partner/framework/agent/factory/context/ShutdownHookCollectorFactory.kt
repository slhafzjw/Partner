package work.slhaf.partner.framework.agent.factory.context

import work.slhaf.partner.framework.agent.exception.FactoryExecutionException
import work.slhaf.partner.framework.agent.factory.AgentBaseFactory
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityCore
import work.slhaf.partner.framework.agent.factory.component.annotation.AgentComponent
import work.slhaf.partner.framework.agent.factory.util.ReflectUtil

/**
 * 校验并收集 `@Shutdown` 方法。
 *
 * 当前规则:
 * - `@Shutdown` 仅允许位于 `@AgentComponent` 相关类或 `@CapabilityCore` 相关类。
 * - `@Shutdown` 方法不能包含形参。
 *
 * 收集通过后，统一调用 `AgentContext.addShutdownHook` 注册。
 */
class ShutdownHookCollectorFactory : AgentBaseFactory() {
    private val factoryName = "shutdown-hook-collector-factory"

    override fun execute(context: AgentRegisterContext) {
        val reflections = context.reflections
        val agentContext = context.agentContext

        reflections.getMethodsAnnotatedWith(Shutdown::class.java)
            .forEach { method ->
                val declaringClass = method.declaringClass
                val isAgentComponentRelated =
                    ReflectUtil.isAssignableFromAnnotation(declaringClass, AgentComponent::class.java)
                val isCapabilityCoreRelated =
                    ReflectUtil.isAssignableFromAnnotation(declaringClass, CapabilityCore::class.java)

                if (!isAgentComponentRelated && !isCapabilityCoreRelated) {
                    throw FactoryExecutionException(
                        "@Shutdown can only be declared on AgentComponent/CapabilityCore classes: ${declaringClass.name}#${method.name}",
                        factoryName
                    )
                }
                if (method.parameterCount > 0) {
                    throw FactoryExecutionException(
                        "@Shutdown methods must not declare parameters: ${declaringClass.name}#${method.name}",
                        factoryName
                    )
                }

                val order = method.getAnnotation(Shutdown::class.java).order
                val added = agentContext.addShutdownHook(method, order)
                if (!added) {
                    throw FactoryExecutionException(
                        "Failed to collect shutdown hook: ${declaringClass.name}#${method.name}",
                        factoryName
                    )
                }
            }
    }
}
