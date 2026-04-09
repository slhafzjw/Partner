package work.slhaf.partner.framework.agent.factory.context

import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder
import java.lang.reflect.Method
import java.net.URL

class AgentRegisterContext(urls: List<URL>) {
    val reflections: Reflections = Reflections(
        ConfigurationBuilder().setScanners(
            Scanners.FieldsAnnotated,
            Scanners.SubTypes,
            Scanners.MethodsAnnotated,
            Scanners.TypesAnnotated
        ).setUrls(urls)
    )

    val capabilityFactoryContext: CapabilityFactoryContext = CapabilityFactoryContext()
    val componentFactoryContext: ComponentFactoryContext = ComponentFactoryContext()
    val agentContext: AgentContext = AgentContext
}

class CapabilityFactoryContext {
    val cores: MutableSet<Class<*>> = LinkedHashSet()
    val capabilities: MutableSet<Class<*>> = LinkedHashSet()
    val methods: MutableSet<Method> = LinkedHashSet()
}

class ComponentFactoryContext {
    val initMethodsByDeclaringType: MutableMap<Class<*>, MutableSet<Method>> = LinkedHashMap()
}
