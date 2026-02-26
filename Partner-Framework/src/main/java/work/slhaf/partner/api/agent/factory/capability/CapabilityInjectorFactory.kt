package work.slhaf.partner.api.agent.factory.capability

import work.slhaf.partner.api.agent.factory.AgentBaseFactory
import work.slhaf.partner.api.agent.factory.capability.annotation.Capability
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability
import work.slhaf.partner.api.agent.factory.capability.exception.CapabilityProxySetFailedException
import work.slhaf.partner.api.agent.factory.context.AgentContext
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext
import java.lang.reflect.Field
import java.lang.reflect.Modifier

class CapabilityInjectorFactory : AgentBaseFactory() {
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
            } catch (e: CapabilityProxySetFailedException) {
                throw e
            } catch (e: Exception) {
                throw CapabilityProxySetFailedException(
                    "Capability 注入失败: ${target::class.java.name}#${field.name}",
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
            ?: throw CapabilityProxySetFailedException(
                "InjectCapability 字段类型未标注 @Capability: ${targetClass.name}#${field.name} -> ${field.type.name}"
            )

        val capabilityValue = capability.value
        val implementation = capabilityMap[capabilityValue] ?: throw CapabilityProxySetFailedException(
            "未找到可注入 Capability 实例: ${targetClass.name}#${field.name} -> $capabilityValue"
        )
        if (!field.type.isAssignableFrom(implementation.instance.javaClass)) {
            throw CapabilityProxySetFailedException(
                "Capability 实例类型不匹配: ${targetClass.name}#${field.name} -> $capabilityValue"
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
