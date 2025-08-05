package work.slhaf.partner.api.factory.module.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 仅适用于以下类中的方法:
 * 1. <code>@AgentModule</code>注解所在类
 * 2. <code>ActivateModel</code>子类
 * 3. <code>AgentInteractionModule</code>或者<code>AgentInteractionSubModule</code>子类
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BeforeExecute {
    int order() default 0;
}
