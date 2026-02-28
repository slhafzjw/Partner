package work.slhaf.partner.api.agent.factory.capability

import work.slhaf.partner.api.agent.factory.AgentBaseFactory
import work.slhaf.partner.api.agent.factory.capability.annotation.Capability
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod
import work.slhaf.partner.api.agent.factory.capability.exception.CapabilityCoreInstancesCreateFailedException
import work.slhaf.partner.api.agent.factory.capability.exception.CapabilityFactoryExecuteFailedException
import work.slhaf.partner.api.agent.factory.capability.exception.DuplicateMethodException
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext
import work.slhaf.partner.api.agent.util.AgentUtil.methodSignature
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.function.Function

/**
 * 基于 `CapabilityFactoryContext` 中已校验结果构建 Capability 运行时对象。
 *
 * 行为:
 * - 实例化所有 `@CapabilityCore`。
 * - 构建方法路由表（`value + methodSignature`）并检测重复路由。
 * - 为每个 `@Capability` 生成 JDK 动态代理，将接口调用路由到对应 core 方法。
 * - 将 capability 代理、cores 与方法映射注册到 `AgentContext`。
 */
class CapabilityRegisterFactory : AgentBaseFactory() {
    override fun execute(context: AgentRegisterContext) {
        val capabilityFactoryContext = context.capabilityFactoryContext
        val agentContext = context.agentContext

        val cores = capabilityFactoryContext.cores.toSet()
        val capabilities = capabilityFactoryContext.capabilities.toSet()
        if (cores.isEmpty() || capabilities.isEmpty()) {
            throw CapabilityFactoryExecuteFailedException("CapabilityFactoryContext 中缺少已校验的 capability/core 信息")
        }

        val coreInstances = LinkedHashMap<Class<*>, Any>()
        val methodsRouterTable = LinkedHashMap<String, Function<Array<Any?>, Any?>>()

        setCoreInstances(cores, coreInstances)
        val methodBindingMap = buildMethodBindingMap(cores, coreInstances, methodsRouterTable)

        capabilities.forEach { capabilityType ->
            val capabilityValue = capabilityType.getAnnotation(Capability::class.java).value
            val proxy = createCapabilityProxy(capabilityType, capabilityValue, methodsRouterTable)
            val methods = buildCapabilityMethodMap(capabilityType, capabilityValue, methodBindingMap)
            val cores = buildCapabilityCoreMap(capabilityValue, coreInstances)
            agentContext.addCapability(capabilityValue, proxy, cores, methods)
        }
    }

    private fun setCoreInstances(
        cores: Set<Class<*>>,
        coreInstances: MutableMap<Class<*>, Any>
    ) {
        try {
            cores.forEach { core ->
                val constructor = core.getDeclaredConstructor()
                constructor.isAccessible = true
                coreInstances[core] = constructor.newInstance()
            }
        } catch (e: Exception) {
            throw CapabilityCoreInstancesCreateFailedException("core实例创建失败", e)
        }
    }

    private fun buildMethodBindingMap(
        cores: Set<Class<*>>,
        coreInstances: Map<Class<*>, Any>,
        methodsRouterTable: MutableMap<String, Function<Array<Any?>, Any?>>
    ): Map<String, MethodBinding> {
        val map = LinkedHashMap<String, MethodBinding>()
        cores.forEach { core ->
            val capabilityValue = core.getAnnotation(CapabilityCore::class.java).value
            val coreInstance = coreInstances[core]
                ?: throw CapabilityFactoryExecuteFailedException("未找到CapabilityCore实例: ${core.name}")

            core.methods
                .filter { it.isAnnotationPresent(CapabilityMethod::class.java) }
                .forEach { method ->
                    val key = "$capabilityValue.${methodSignature(method)}"
                    if (map.containsKey(key) || methodsRouterTable.containsKey(key)) {
                        throw DuplicateMethodException("重复注册能力方法: ${core.name}#${method.name}")
                    }
                    map[key] = MethodBinding(core, coreInstance, method)
                    methodsRouterTable[key] = Function { args ->
                        invokeMethod(coreInstance, method, args)
                    }
                }
        }
        return map
    }

    private fun createCapabilityProxy(
        capabilityType: Class<*>,
        capabilityValue: String,
        methodsRouterTable: Map<String, Function<Array<Any?>, Any?>>
    ): Any {
        return Proxy.newProxyInstance(
            capabilityType.classLoader,
            arrayOf(capabilityType)
        ) { proxy, method, args ->
            when (method.name) {
                "toString" -> "CapabilityProxy($capabilityValue)"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> {
                    val key = "$capabilityValue.${methodSignature(method)}"
                    val fn = methodsRouterTable[key]
                        ?: throw CapabilityFactoryExecuteFailedException("未找到能力方法路由: $key")

                    @Suppress("UNCHECKED_CAST")
                    val actualArgs = (args ?: emptyArray<Any?>()) as Array<Any?>
                    fn.apply(actualArgs)
                }
            }
        }
    }

    private fun buildCapabilityMethodMap(
        capabilityType: Class<*>,
        capabilityValue: String,
        methodBindingMap: Map<String, MethodBinding>
    ): Map<String, Method> {
        val methods = LinkedHashMap<String, Method>()
        capabilityType.methods.forEach { method ->
            if (method.declaringClass == Any::class.java) {
                return@forEach
            }
            val key = "$capabilityValue.${methodSignature(method)}"
            val binding = methodBindingMap[key]
                ?: throw CapabilityFactoryExecuteFailedException("Capability方法缺少实现: $key")
            methods[key] = binding.method
        }
        return methods
    }

    private fun buildCapabilityCoreMap(
        capabilityValue: String,
        coreInstances: Map<Class<*>, Any>
    ): Map<Class<*>, Any> {
        return coreInstances
            .filterKeys { coreType ->
                coreType.getAnnotation(CapabilityCore::class.java)?.value == capabilityValue
            }
            .toMap()
    }

    private fun invokeMethod(instance: Any, method: Method, args: Array<Any?>): Any? {
        return try {
            method.invoke(instance, *args)
        } catch (e: Exception) {
            throw CapabilityFactoryExecuteFailedException(
                "能力方法调用失败: ${instance::class.java.name}#${method.name}",
                e
            )
        }
    }

    private data class MethodBinding(
        val coreType: Class<*>,
        val instance: Any,
        val method: Method
    )
}
