package work.slhaf.partner.framework.agent.factory.capability.annotation;

import work.slhaf.partner.framework.agent.factory.component.annotation.AgentComponent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于注入`Capability`,适用于{@link AgentComponent} 实例
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InjectCapability {
}
