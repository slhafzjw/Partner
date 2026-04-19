package work.slhaf.partner.framework.agent.factory.context

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import org.slf4j.LoggerFactory
import work.slhaf.partner.framework.agent.exception.AgentRuntimeException
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityCore
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule
import work.slhaf.partner.framework.agent.factory.component.annotation.AgentComponent
import work.slhaf.partner.framework.agent.interaction.flow.RunningFlowContext
import java.lang.reflect.Method
import java.time.ZonedDateTime

object AgentContext {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val _modules =
        mutableMapOf<String, ModuleContextData<AbstractAgentModule>>()

    val modules: Map<String, ModuleContextData<AbstractAgentModule>>
        get() = _modules

    private val _capabilities =
        mutableMapOf<String, CapabilityImplementation>()

    val capabilities: Map<String, CapabilityImplementation>
        get() = _capabilities

    private val _additionalComponents = mutableMapOf<Class<*>, Any>()

    val additionalComponents: Map<Class<*>, Any>
        get() = _additionalComponents

    private val _metadata: MutableMap<String, MetaDataContent> = mutableMapOf()

    val metadata: Map<String, MetaDataContent>
        get() = _metadata

    private val shutdownHooks = mutableMapOf<ShutdownHookDesc.Type, MutableList<ShutdownHookDesc>>()
    private val preShutdownHooks = mutableListOf<LifecycleShutdownHookDesc>()
    private val postShutdownHooks = mutableListOf<LifecycleShutdownHookDesc>()

    init {
        installShutdownHook()
    }

    fun addModule(name: String, module: ModuleContextData<AbstractAgentModule>) {
        _modules[name] = module
    }

    fun addCapability(
        capability: String,
        instance: Any,
        cores: Map<Class<*>, Any>,
        methods: Map<String, Method>
    ) {
        val newImpl = CapabilityImplementation(instance.javaClass, instance, cores, methods)
        _capabilities[capability] = newImpl
    }

    fun addAdditionalComponent(instance: Any): Boolean {
        val type = instance::class.java
        if (!type.isAnnotationPresent(AgentComponent::class.java)) {
            return false
        }
        _additionalComponents[type] = instance
        return true
    }

    fun addMetadata(name: String, value: Any) {
        val content = MetaDataContent(value::class.java, JSONObject.toJSONString(value))
        _metadata[name] = content
    }

    fun addShutdownHook(method: Method, order: Int): Boolean {
        if (!method.isAnnotationPresent(Shutdown::class.java)) {
            return false
        }
        val clazz = method.declaringClass
        val type = if (AbstractAgentModule.Running::class.java.isAssignableFrom(clazz)) {
            ShutdownHookDesc.Type.RUNNING
        } else if (AbstractAgentModule.Sub::class.java.isAssignableFrom(clazz)) {
            ShutdownHookDesc.Type.SUB
        } else if (AbstractAgentModule.Standalone::class.java.isAssignableFrom(clazz)) {
            ShutdownHookDesc.Type.STANDALONE
        } else if (clazz.isAnnotationPresent(AgentComponent::class.java)) {
            ShutdownHookDesc.Type.ADDITIONAL
        } else if (clazz.isAnnotationPresent(CapabilityCore::class.java)) {
            ShutdownHookDesc.Type.CAPABILITY
        } else {
            return false
        }
        shutdownHooks.computeIfAbsent(type) { mutableListOf() }.add(ShutdownHookDesc(clazz, order, method, type))
        return true
    }

    fun addPreShutdownHook(name: String, order: Int = 0, action: Runnable) {
        preShutdownHooks.add(LifecycleShutdownHookDesc(name, order, action))
    }

    fun addPostShutdownHook(name: String, order: Int = 0, action: Runnable) {
        postShutdownHooks.add(LifecycleShutdownHookDesc(name, order, action))
    }

    private fun installShutdownHook() {

        class Instances(
            val running: MutableMap<Class<out AbstractAgentModule.Running<out RunningFlowContext>>, Any> = mutableMapOf(),
            val standalone: MutableMap<Class<out AbstractAgentModule.Standalone>, Any> = mutableMapOf(),
            val sub: MutableMap<Class<out AbstractAgentModule.Sub<*, *>>, Any> = mutableMapOf(),
            val additional: MutableMap<Class<*>, Any> = mutableMapOf(),
            val capability: MutableMap<Class<*>, Any> = mutableMapOf()
        )

        fun computeInstances(): Instances {
            val instances = Instances()
            modules.values.forEach {
                when (it) {
                    is ModuleContextData.Running<*> -> instances.running[it.clazz] = it.instance
                    is ModuleContextData.Standalone<*> -> instances.standalone[it.clazz] = it.instance
                    is ModuleContextData.Sub<*> -> instances.sub[it.clazz] = it.instance
                }

            }
            instances.additional.putAll(additionalComponents)
            capabilities.values.forEach {
                instances.capability.putAll(it.cores)
            }
            return instances
        }


        fun getModuleInstance(clazz: Class<*>, instances: Instances): Any? {
            return if (AbstractAgentModule.Running::class.java.isAssignableFrom(clazz)) {
                instances.running[clazz]
            } else if (AbstractAgentModule.Sub::class.java.isAssignableFrom(clazz)) {
                instances.sub[clazz]
            } else if (AbstractAgentModule.Standalone::class.java.isAssignableFrom(clazz)) {
                instances.standalone[clazz]
            } else {
                null
            }
        }

        fun getInstanceOf(clazz: Class<*>, type: ShutdownHookDesc.Type, instances: Instances): Any {
            val instance = when (type) {
                ShutdownHookDesc.Type.RUNNING -> getModuleInstance(clazz, instances)
                ShutdownHookDesc.Type.STANDALONE -> getModuleInstance(clazz, instances)
                ShutdownHookDesc.Type.SUB -> getModuleInstance(clazz, instances)
                ShutdownHookDesc.Type.ADDITIONAL -> instances.additional[clazz]
                ShutdownHookDesc.Type.CAPABILITY -> instances.capability[clazz]
            }
            if (instance == null) {
                throw AgentRuntimeException("Instance of type $type not found")
            }
            return instance
        }

        fun trigger(hooks: List<ShutdownHookDesc>, instances: Instances) {
            hooks.sortedBy { it.order }
                .forEach {
                    try {
                        it.method.invoke(getInstanceOf(it.clazz, it.type, instances))
                    } catch (e: Exception) {
                        log.error("Failed to invoke shutdown hook ${it.clazz.simpleName}#${it.method.name}", e)
                    }
                }
        }

        fun triggerLifecycleHooks(hooks: List<LifecycleShutdownHookDesc>) {
            hooks.sortedBy { it.order }
                .forEach {
                    try {
                        it.action.run()
                    } catch (e: Exception) {
                        log.error("Failed to invoke lifecycle shutdown hook {}", it.name, e)
                    }
                }
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            log.info("Shutdown hooks triggering...")
            val instances = computeInstances()
            triggerLifecycleHooks(preShutdownHooks)
            shutdownHooks[ShutdownHookDesc.Type.RUNNING]?.let { trigger(it, instances) }
            shutdownHooks[ShutdownHookDesc.Type.ADDITIONAL]?.let { trigger(it, instances) }
            shutdownHooks[ShutdownHookDesc.Type.STANDALONE]?.let { trigger(it, instances) }
            shutdownHooks[ShutdownHookDesc.Type.SUB]?.let { trigger(it, instances) }
            shutdownHooks[ShutdownHookDesc.Type.CAPABILITY]?.let { trigger(it, instances) }
            triggerLifecycleHooks(postShutdownHooks)
            log.info("Shutdown hooks triggered...")
        })
    }

    data class MetaDataContent(
        val clazz: Class<*>,
        val value: String
    )

    data class CapabilityImplementation(
        val clazz: Class<*>,
        val instance: Any,
        val cores: Map<Class<*>, Any>,
        val methods: Map<String, Method>
    )
}

sealed class ModuleContextData<out T : AbstractAgentModule> {
    abstract val clazz: Class<out T>
    abstract val instance: T
    abstract val launchTime: ZonedDateTime
    abstract val modelInfo: ModelInfo?

    val metadata = mutableMapOf<String, Any>()

    data class Running<T : AbstractAgentModule.Running<*>>(
        override val clazz: Class<T>,
        override val instance: T,
        override val launchTime: ZonedDateTime,
        override val modelInfo: ModelInfo?,

        val order: Int
    ) : ModuleContextData<T>()

    data class Sub<T : AbstractAgentModule.Sub<*, *>>(
        override val clazz: Class<T>,
        override val instance: T,
        override val launchTime: ZonedDateTime,
        override val modelInfo: ModelInfo?,

        val injectTarget: MutableSet<AbstractAgentModule> = mutableSetOf()
    ) : ModuleContextData<T>()

    data class Standalone<T : AbstractAgentModule.Standalone>(
        override val clazz: Class<T>,
        override val instance: T,
        override val launchTime: ZonedDateTime,
        override val modelInfo: ModelInfo?,

        val injectTarget: MutableSet<AbstractAgentModule> = mutableSetOf()
    ) : ModuleContextData<T>()

    data class ModelInfo(
        val modelKey: String,
        val basePrompt: JSONArray
    )
}

/**
 * # Shutdown Hook 注解
 * - 可用于[AgentComponent]相关类、[work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityCore]相关类。
 * - 关闭时将按照：Running -> Additional -> Standalone -> Sub -> Capability 的顺序执行
 * - [order] 仅在同一层级内起顺序对比作用，数值越小，执行越早。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Shutdown(
    val order: Int = 0,
)

data class ShutdownHookDesc(
    val clazz: Class<*>,
    val order: Int,
    val method: Method,
    val type: Type
) {
    enum class Type {
        RUNNING,
        ADDITIONAL,
        STANDALONE,
        SUB,
        CAPABILITY
    }
}

data class LifecycleShutdownHookDesc(
    val name: String,
    val order: Int,
    val action: Runnable
)
