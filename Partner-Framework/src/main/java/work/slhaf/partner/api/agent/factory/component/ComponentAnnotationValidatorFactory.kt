package work.slhaf.partner.api.agent.factory.component

import work.slhaf.partner.api.agent.factory.AgentBaseFactory
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule
import work.slhaf.partner.api.agent.factory.component.annotation.AgentComponent
import work.slhaf.partner.api.agent.factory.component.annotation.Init
import work.slhaf.partner.api.agent.factory.component.annotation.InjectModule
import work.slhaf.partner.api.agent.factory.component.exception.ModuleCheckException
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext
import work.slhaf.partner.api.agent.util.AgentUtil

class ComponentAnnotationValidatorFactory : AgentBaseFactory() {
    override fun execute(context: AgentRegisterContext) {
        val reflections = context.reflections

        reflections.getMethodsAnnotatedWith(Init::class.java)
            .forEach { method ->
                val declaringClass = method.declaringClass
                if (!AgentUtil.isAssignableFromAnnotation(declaringClass, AgentComponent::class.java)) {
                    throw ModuleCheckException(
                        "@Init 只能用于 AgentComponent 中: " +
                                "${declaringClass.name}#${method.name}"
                    )
                }
            }

        reflections.getFieldsAnnotatedWith(InjectModule::class.java)
            .forEach { field ->
                val declaringClass = field.declaringClass
                if (!AgentUtil.isAssignableFromAnnotation(declaringClass, AgentComponent::class.java)) {
                    throw ModuleCheckException(
                        "@InjectModule 只能用于 AgentComponent 中: " +
                                "${declaringClass.name}#${field.name}"
                    )
                }
                if (AbstractAgentModule.Running::class.java.isAssignableFrom(field.type)) {
                    throw ModuleCheckException(
                        "@InjectModule 不可注入 AbstractAgentModule.Running 子类: " +
                                "${declaringClass.name}#${field.name} -> ${field.type.name}"
                    )
                }
            }
    }
}
