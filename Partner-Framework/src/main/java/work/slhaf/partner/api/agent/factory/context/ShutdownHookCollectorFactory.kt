package work.slhaf.partner.api.agent.factory.context

import work.slhaf.partner.api.agent.factory.AgentBaseFactory
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore
import work.slhaf.partner.api.agent.factory.component.annotation.AgentComponent
import work.slhaf.partner.api.agent.factory.component.exception.ModuleCheckException
import work.slhaf.partner.api.agent.util.AgentUtil

class ShutdownHookCollectorFactory : AgentBaseFactory() {
    override fun execute(context: AgentRegisterContext) {
        val reflections = context.reflections
        val agentContext = context.agentContext

        reflections.getMethodsAnnotatedWith(Shutdown::class.java)
            .forEach { method ->
                val declaringClass = method.declaringClass
                val isAgentComponentRelated =
                    AgentUtil.isAssignableFromAnnotation(declaringClass, AgentComponent::class.java)
                val isCapabilityCoreRelated =
                    AgentUtil.isAssignableFromAnnotation(declaringClass, CapabilityCore::class.java)

                if (!isAgentComponentRelated && !isCapabilityCoreRelated) {
                    throw ModuleCheckException(
                        "@Shutdown 仅能用于 AgentComponent/CapabilityCore 相关类: " +
                                "${declaringClass.name}#${method.name}"
                    )
                }
                if (method.parameterCount > 0) {
                    throw ModuleCheckException(
                        "@Shutdown 标注的方法不能包含形参: " +
                                "${declaringClass.name}#${method.name}"
                    )
                }

                val order = method.getAnnotation(Shutdown::class.java).order
                val added = agentContext.addShutdownHook(method, order)
                if (!added) {
                    throw ModuleCheckException(
                        "ShutdownHook 收集失败: ${declaringClass.name}#${method.name}"
                    )
                }
            }
    }
}
