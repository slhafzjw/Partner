package work.slhaf.partner.framework.agent.factory.component

import work.slhaf.partner.framework.agent.exception.FactoryExecutionException
import work.slhaf.partner.framework.agent.factory.AgentBaseFactory
import work.slhaf.partner.framework.agent.factory.component.annotation.Init
import work.slhaf.partner.framework.agent.factory.context.AgentContext
import work.slhaf.partner.framework.agent.factory.context.AgentRegisterContext
import work.slhaf.partner.framework.agent.factory.util.ReflectUtil.methodSignature
import java.lang.reflect.Method

/**
 * 执行 Component 的 `@Init` 生命周期方法。
 *
 * `@Init` 方法来源于 [work.slhaf.partner.framework.agent.factory.context.ComponentFactoryContext]，
 * 执行目标包括 modules 与 additionalComponents，按 `order` 升序执行。
 */
class ComponentInitHookExecutorFactory : AgentBaseFactory() {
    private val factoryName = "component-init-hook-executor-factory"

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
                    throw FactoryExecutionException(
                        "Init method must not declare parameters: ${target::class.java.name}#${methodSignature(method)}",
                        factoryName
                    )
                }
                method.isAccessible = true
                method.invoke(target)
            } catch (e: FactoryExecutionException) {
                throw e
            } catch (e: Exception) {
                throw FactoryExecutionException(
                    "Failed to execute init hook: ${target::class.java.name}#${methodSignature(method)}",
                    factoryName,
                    e
                )
            }
        }
    }
}
