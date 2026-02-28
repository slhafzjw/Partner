package work.slhaf.partner.api.agent.factory.component

import work.slhaf.partner.api.agent.factory.AgentBaseFactory
import work.slhaf.partner.api.agent.factory.component.annotation.Init
import work.slhaf.partner.api.agent.factory.component.exception.ModuleInitHookExecuteFailedException
import work.slhaf.partner.api.agent.factory.context.AgentContext
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext
import work.slhaf.partner.api.agent.util.AgentUtil.methodSignature
import java.lang.reflect.Method

class ComponentInitHookExecutorFactory : AgentBaseFactory() {
    override fun execute(context: AgentRegisterContext) {
        val initMethodsByDeclaringType = context.componentFactoryContext.initMethodsByDeclaringType
        val targets = buildTargets(context.agentContext)

        targets.forEach { target ->
            val initMethods = collectInitMethods(target::class.java, initMethodsByDeclaringType)
            executeInitMethods(target, initMethods)
        }
    }

    private fun buildTargets(agentContext: AgentContext): List<Any> {
        val moduleInstances = agentContext.modules.values.map { it.instance }
        return moduleInstances + agentContext.additionalComponents.values
    }

    private fun collectInitMethods(
        targetType: Class<*>,
        initMethodsByDeclaringType: Map<Class<*>, Set<Method>>
    ): List<Method> {
        val methods = LinkedHashSet<Method>()
        var current: Class<*>? = targetType
        while (current != null && current != Any::class.java) {
            initMethodsByDeclaringType[current]?.forEach { methods.add(it) }
            current = current.superclass
        }
        return methods
            .sortedBy { it.getAnnotation(Init::class.java)?.order ?: 0 }
    }

    private fun executeInitMethods(target: Any, initMethods: List<Method>) {
        initMethods.forEach { method ->
            try {
                if (method.parameterCount > 0) {
                    throw ModuleInitHookExecuteFailedException(
                        "Init方法不支持参数: ${target::class.java.name}#${methodSignature(method)}"
                    )
                }
                method.isAccessible = true
                method.invoke(target)
            } catch (e: ModuleInitHookExecuteFailedException) {
                throw e
            } catch (e: Exception) {
                throw ModuleInitHookExecuteFailedException(
                    "模块的init hook方法执行失败! 模块: ${target::class.java.simpleName} 方法签名: ${
                        methodSignature(
                            method
                        )
                    }",
                    e
                )
            }
        }
    }
}

