package work.slhaf.partner.framework.agent.factory.capability

import cn.hutool.core.util.ClassUtil
import org.reflections.Reflections
import work.slhaf.partner.framework.agent.factory.AgentBaseFactory
import work.slhaf.partner.framework.agent.factory.capability.annotation.Capability
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityCore
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityMethod
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability
import work.slhaf.partner.framework.agent.factory.capability.exception.DuplicateCapabilityException
import work.slhaf.partner.framework.agent.factory.capability.exception.UnMatchedCapabilityException
import work.slhaf.partner.framework.agent.factory.capability.exception.UnMatchedCapabilityMethodException
import work.slhaf.partner.framework.agent.factory.component.annotation.AgentComponent
import work.slhaf.partner.framework.agent.factory.context.AgentRegisterContext
import work.slhaf.partner.framework.agent.factory.util.ReflectUtil.isAssignableFromAnnotation
import work.slhaf.partner.framework.agent.factory.util.ReflectUtil.methodSignature

/**
 * 校验 Capability 体系注解关系，并将扫描结果写入 `CapabilityFactoryContext`。
 *
 * 当前规则:
 * - `@Capability` 的 value 全局唯一。
 * - `@CapabilityMethod` 仅允许出现在 `@CapabilityCore` 类中。
 * - 每个 `@Capability` 接口方法必须在同 value 的 core 集合中存在且仅存在一个实现。
 * - `@InjectCapability` 字段仅允许位于 `@AgentComponent` 相关类中。
 */
class CapabilityAnnotationValidatorFactory : AgentBaseFactory() {
    override fun execute(context: AgentRegisterContext) {
        val reflections = context.reflections
        val cores = loadCores(reflections)
        val capabilities = loadCapabilities(reflections)
        val methods = loadCapabilityMethods(reflections)

        checkCapabilityUniqueByValue(capabilities)
        checkCapabilityMethodLocation(methods)
        checkCapabilityMethodsImplementedUniquely(cores, capabilities)
        checkInjectCapability(reflections)
        storeValidatedScanResult(context, cores, capabilities, methods)
    }

    private fun loadCores(reflections: Reflections): Set<Class<*>> {
        return reflections.getTypesAnnotatedWith(CapabilityCore::class.java)
            .filter { ClassUtil.isNormalClass(it) }
            .toSet()
    }

    private fun loadCapabilities(reflections: Reflections): Set<Class<*>> {
        return reflections.getTypesAnnotatedWith(Capability::class.java).toSet()
    }

    private fun loadCapabilityMethods(reflections: Reflections): Set<java.lang.reflect.Method> {
        return reflections.getMethodsAnnotatedWith(CapabilityMethod::class.java).toSet()
    }

    /**
     * 规则1: @Capability 按 value 唯一
     */
    private fun checkCapabilityUniqueByValue(capabilities: Set<Class<*>>) {
        val capabilityByValue = LinkedHashMap<String, Class<*>>()
        capabilities.forEach { capability ->
            val value = capability.getAnnotation(Capability::class.java).value
            val existed = capabilityByValue.putIfAbsent(value, capability)
            if (existed != null) {
                throw DuplicateCapabilityException("Capability 注册异常: 重复的Capability接口: $value")
            }
        }
    }

    /**
     * 规则3.1: @CapabilityMethod 仅能用于 @CapabilityCore 类
     */
    private fun checkCapabilityMethodLocation(methods: Set<java.lang.reflect.Method>) {
        methods.forEach { method ->
            val declaringClass = method.declaringClass
            if (!declaringClass.isAnnotationPresent(CapabilityCore::class.java)) {
                throw UnMatchedCapabilityException(
                    "@CapabilityMethod 仅能用于 @CapabilityCore 所标注类中: " +
                            "${declaringClass.name}#${method.name}"
                )
            }
        }
    }

    /**
     * 规则3.2:
     * @Capability 接口方法，必须在对应 value 的 @CapabilityCore 集合中存在唯一实现
     */
    private fun checkCapabilityMethodsImplementedUniquely(
        cores: Set<Class<*>>,
        capabilities: Set<Class<*>>
    ) {
        val coreMethodsByValue = LinkedHashMap<String, MutableMap<String, MutableList<String>>>()
        cores.forEach { core ->
            val value = core.getAnnotation(CapabilityCore::class.java).value
            val signatureMap = coreMethodsByValue.computeIfAbsent(value) { LinkedHashMap() }
            core.methods
                .filter { it.isAnnotationPresent(CapabilityMethod::class.java) }
                .forEach { method ->
                    val signature = methodSignature(method)
                    val implementors = signatureMap.computeIfAbsent(signature) { mutableListOf() }
                    implementors.add(core.name)
                }
        }

        capabilities.forEach { capability ->
            val capabilityValue = capability.getAnnotation(Capability::class.java).value
            val signatureMap = coreMethodsByValue[capabilityValue].orEmpty()
            capability.methods.forEach { method ->
                val signature = methodSignature(method)
                val implementors = signatureMap[signature].orEmpty()
                if (implementors.isEmpty()) {
                    throw UnMatchedCapabilityMethodException(
                        "Capability方法缺少实现: $capabilityValue.$signature"
                    )
                }
                if (implementors.size > 1) {
                    throw UnMatchedCapabilityMethodException(
                        "Capability方法存在多个实现: $capabilityValue.$signature -> ${implementors.joinToString(", ")}"
                    )
                }
            }
        }
    }

    /**
     * 维持现有校验: @InjectCapability 仅能用于 AgentComponent
     */
    private fun checkInjectCapability(reflections: Reflections) {
        reflections.getFieldsAnnotatedWith(InjectCapability::class.java).forEach { field ->
            val declaringClass = field.declaringClass
            if (!isAssignableFromAnnotation(declaringClass, AgentComponent::class.java)) {
                throw UnMatchedCapabilityException(
                    "InjectCapability 注解只能用于 AgentComponent 注解所在类，检查该类是否使用了@CapabilityHolder注解或者受其标注的注解或父类: $declaringClass"
                )
            }
        }
    }

    private fun storeValidatedScanResult(
        context: AgentRegisterContext,
        cores: Set<Class<*>>,
        capabilities: Set<Class<*>>,
        methods: Set<java.lang.reflect.Method>
    ) {
        val capabilityFactoryContext = context.capabilityFactoryContext
        capabilityFactoryContext.cores.clear()
        capabilityFactoryContext.capabilities.clear()
        capabilityFactoryContext.methods.clear()
        capabilityFactoryContext.cores.addAll(cores)
        capabilityFactoryContext.capabilities.addAll(capabilities)
        capabilityFactoryContext.methods.addAll(methods)
    }
}
