package work.slhaf.partner.framework.agent.factory.capability

import work.slhaf.partner.framework.agent.exception.FactoryExecutionException
import work.slhaf.partner.framework.agent.factory.AgentBaseFactory
import work.slhaf.partner.framework.agent.factory.capability.annotation.Capability
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability
import work.slhaf.partner.framework.agent.factory.context.AgentContext
import work.slhaf.partner.framework.agent.factory.context.AgentRegisterContext
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * 将 Capability 代理注入到 `@InjectCapability` 字段。
 *
 * 注入目标来源于 `AgentContext` 的 modules 与 additionalComponents，
 * 注入值来源于 `AgentContext.capabilities`。
 */
class CapabilityInjectorFactory : AgentBaseFactory() {
    private val factoryName = "capability-injector-factory"

    override fun execute(context: AgentRegisterContext) {
        val agentContext = context.agentContext
        val targets = buildTargets(agentContext)
        targets.forEach { target ->
            injectCapabilities(target, agentContext.capabilities)
        }
    }

    private fun buildTargets(agentContext: AgentContext): List<Any> {
        val moduleInstances = agentContext.modules.values.map { it.instance }
        return moduleInstances + agentContext.additionalComponents.values
    }

    private fun injectCapabilities(
        target: Any,
        capabilityMap: Map<String, AgentContext.CapabilityImplementation>
    ) {
        collectInjectFields(target::class.java).forEach { field ->
            try {
                field.isAccessible = true
                val value = resolveCapabilityInstance(field, capabilityMap, target::class.java)
                field.set(target, value)
            } catch (e: FactoryExecutionException) {
                throw e
            } catch (e: Exception) {
                throw FactoryExecutionException(
                    "Failed to inject capability dependency: ${target::class.java.name}#${field.name}",
                    factoryName,
                    e
                )
            }
        }
    }

    private fun resolveCapabilityInstance(
        field: Field,
        capabilityMap: Map<String, AgentContext.CapabilityImplementation>,
        targetClass: Class<*>
    ): Any {
        val capability = field.type.getAnnotation(Capability::class.java)
            ?: throw FactoryExecutionException(
                "InjectCapability target type is not annotated with @Capability: ${targetClass.name}#${field.name} -> ${field.type.name}",
                factoryName
            )

        val capabilityValue = capability.value
        val implementation = capabilityMap[capabilityValue] ?: throw FactoryExecutionException(
            "Injectable capability implementation not found: ${targetClass.name}#${field.name} -> $capabilityValue",
            factoryName
        )
        if (!field.type.isAssignableFrom(implementation.instance.javaClass)) {
            throw FactoryExecutionException(
                "Capability implementation type mismatch: ${targetClass.name}#${field.name} -> $capabilityValue",
                factoryName
            )
        }
        return implementation.instance
    }

    private fun collectInjectFields(clazz: Class<*>): List<Field> {
        val fields = mutableListOf<Field>()
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            current.declaredFields
                .filter { it.isAnnotationPresent(InjectCapability::class.java) }
                .filter { !Modifier.isStatic(it.modifiers) }
                .forEach { fields.add(it) }
            current = current.superclass
        }
        return fields
    }
}
