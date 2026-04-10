package work.slhaf.partner.framework.agent.factory.component

import work.slhaf.partner.framework.agent.exception.FactoryExecutionException
import work.slhaf.partner.framework.agent.factory.AgentBaseFactory
import work.slhaf.partner.framework.agent.factory.component.annotation.InjectModule
import work.slhaf.partner.framework.agent.factory.context.AgentRegisterContext
import work.slhaf.partner.framework.agent.factory.context.ModuleContextData
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * 处理 `@InjectModule` 依赖注入。
 *
 * 注入关系:
 * - `sub + standalone -> running`
 * - `sub -> standalone`
 * - `sub + standalone -> additionalComponent`
 *
 * 当注入目标无匹配实例或存在多个匹配实例时抛出异常。
 */
class ComponentInjectorFactory : AgentBaseFactory() {
    private val factoryName = "component-injector-factory"

    override fun execute(context: AgentRegisterContext) {
        val agentContext = context.agentContext
        val moduleContextList = agentContext.modules.values.toList()

        val runningModules = moduleContextList
            .filterIsInstance<ModuleContextData.Running<*>>()
        val subModules = moduleContextList
            .filterIsInstance<ModuleContextData.Sub<*>>()
        val standaloneModules = moduleContextList
            .filterIsInstance<ModuleContextData.Standalone<*>>()

        val subInstances = subModules.map { it.instance }
        val standaloneInstances = standaloneModules.map { it.instance }

        val providersForRunning = subInstances + standaloneInstances
        val providersForAdditional = subInstances + standaloneInstances

        runningModules.forEach { running ->
            injectIntoTarget(running.instance, providersForRunning)
            subModules.forEach { it.injectTarget.add(running.instance) }
            standaloneModules.forEach { it.injectTarget.add(running.instance) }
        }

        standaloneModules.forEach { standalone ->
            injectIntoTarget(standalone.instance, subInstances)
            subModules.forEach { it.injectTarget.add(standalone.instance) }
        }

        agentContext.additionalComponents.values.forEach { additional ->
            injectIntoTarget(additional, providersForAdditional)
        }
    }

    private fun injectIntoTarget(
        target: Any,
        providers: List<Any>
    ) {
        collectInjectFields(target::class.java).forEach { field ->
            val value = resolveInjectValue(field, providers, target::class.java)
            try {
                field.isAccessible = true
                field.set(target, value)
            } catch (e: IllegalAccessException) {
                throw FactoryExecutionException(
                    "Failed to inject module dependency: ${target::class.java.name}#${field.name}",
                    factoryName,
                    e
                )
            }
        }
    }

    private fun resolveInjectValue(field: Field, providers: List<Any>, targetClass: Class<*>): Any {
        val matched = providers.filter { field.type.isAssignableFrom(it::class.java) }
        if (matched.isEmpty()) {
            throw FactoryExecutionException(
                "No injectable module instance found for: ${targetClass.name}#${field.name} -> ${field.type.name}",
                factoryName
            )
        }
        if (matched.size > 1) {
            throw FactoryExecutionException(
                "Multiple injectable module instances found for: ${targetClass.name}#${field.name} -> ${field.type.name}",
                factoryName
            )
        }
        return matched.first()
    }

    private fun collectInjectFields(clazz: Class<*>): List<Field> {
        val fields = mutableListOf<Field>()
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            current.declaredFields
                .filter { it.isAnnotationPresent(InjectModule::class.java) }
                .filter { !Modifier.isStatic(it.modifiers) }
                .forEach { fields.add(it) }
            current = current.superclass
        }
        return fields
    }
}
