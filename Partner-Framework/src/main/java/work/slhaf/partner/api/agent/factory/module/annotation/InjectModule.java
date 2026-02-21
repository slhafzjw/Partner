package work.slhaf.partner.api.agent.factory.module.annotation;

import work.slhaf.partner.api.agent.factory.AgentComponent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 模块注入 Hook，适用于{@link AgentComponent} 实例
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InjectModule {
}
