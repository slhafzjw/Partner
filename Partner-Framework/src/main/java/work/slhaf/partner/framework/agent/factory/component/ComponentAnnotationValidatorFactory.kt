package work.slhaf.partner.framework.agent.factory.component

import work.slhaf.partner.framework.agent.exception.FactoryExecutionException
import work.slhaf.partner.framework.agent.factory.AgentBaseFactory
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule
import work.slhaf.partner.framework.agent.factory.component.annotation.AgentComponent
import work.slhaf.partner.framework.agent.factory.component.annotation.Init
import work.slhaf.partner.framework.agent.factory.component.annotation.InjectModule
import work.slhaf.partner.framework.agent.factory.context.AgentRegisterContext
import work.slhaf.partner.framework.agent.factory.util.ReflectUtil

/**
 * 校验 Component 层面的注解约束，并缓存 Init 方法扫描结果。
 *
 * 当前规则:
 * - `@Init` 仅能用于 `@AgentComponent` 相关类，且方法不能包含形参。
 * - `@InjectModule` 仅能用于 `@AgentComponent` 相关类，且字段类型不能是 Running 模块。
 * - 通过校验的 `@Init` 方法按声明类存入 `ComponentFactoryContext`。
 */
class ComponentAnnotationValidatorFactory : AgentBaseFactory() {
    private val factoryName = "component-annotation-validator-factory"

    override fun execute(context: AgentRegisterContext) {
        val reflections = context.reflections
        val componentFactoryContext = context.componentFactoryContext
        componentFactoryContext.initMethodsByDeclaringType.clear()

        reflections.getMethodsAnnotatedWith(Init::class.java)
            .forEach { method ->
                val declaringClass = method.declaringClass
                if (!ReflectUtil.isAssignableFromAnnotation(declaringClass, AgentComponent::class.java)) {
                    throw FactoryExecutionException(
                        "@Init can only be declared on AgentComponent classes: ${declaringClass.name}#${method.name}",
                        factoryName
                    )
                }
                if (method.parameterCount > 0) {
                    throw FactoryExecutionException(
                        "@Init methods must not declare parameters: ${declaringClass.name}#${method.name}",
                        factoryName
                    )
                }
                val methods = componentFactoryContext
                    .initMethodsByDeclaringType
                    .getOrPut(declaringClass) { LinkedHashSet() }
                methods.add(method)
            }

        reflections.getFieldsAnnotatedWith(InjectModule::class.java)
            .forEach { field ->
                val declaringClass = field.declaringClass
                if (!ReflectUtil.isAssignableFromAnnotation(declaringClass, AgentComponent::class.java)) {
                    throw FactoryExecutionException(
                        "@InjectModule can only be declared on AgentComponent classes: ${declaringClass.name}#${field.name}",
                        factoryName
                    )
                }
                if (AbstractAgentModule.Running::class.java.isAssignableFrom(field.type)) {
                    throw FactoryExecutionException(
                        "@InjectModule cannot target AbstractAgentModule.Running subclasses: ${declaringClass.name}#${field.name} -> ${field.type.name}",
                        factoryName
                    )
                }
            }
    }
}
