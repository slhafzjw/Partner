package work.slhaf.partner.api.agent.factory.module.annotation;

import work.slhaf.partner.api.agent.factory.AgentComponent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 初始化 Hook，适用于{@link AgentComponent} 实例
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Init {
    int order() default 0;
}
