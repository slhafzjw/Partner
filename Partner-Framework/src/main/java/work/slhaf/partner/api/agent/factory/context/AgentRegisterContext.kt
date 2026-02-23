package work.slhaf.partner.api.agent.factory.context

import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder
import work.slhaf.partner.api.agent.factory.config.pojo.ModelConfig
import work.slhaf.partner.api.chat.pojo.Message
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

    val configFactoryContext: ConfigFactoryContext = ConfigFactoryContext()
    val capabilityFactoryContext: CapabilityFactoryContext = CapabilityFactoryContext()
    val agentContext: AgentContext = AgentContext
}

class ConfigFactoryContext {
    val modelPromptMap: HashMap<String, List<Message>> = HashMap()
    val modelConfigMap: HashMap<String, ModelConfig> = HashMap()
}

class CapabilityFactoryContext {
    val cores: MutableSet<Class<*>> = LinkedHashSet()
    val capabilities: MutableSet<Class<*>> = LinkedHashSet()
    val methods: MutableSet<Method> = LinkedHashSet()
}
