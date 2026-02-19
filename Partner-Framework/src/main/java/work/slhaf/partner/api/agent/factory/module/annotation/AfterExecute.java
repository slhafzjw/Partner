package work.slhaf.partner.api.agent.factory.module.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 仅适用于以下类中的方法:
 * 1. <code>@AgentRunningModule</code>注解所在类
 * 2. <code>ActivateModel</code>子类
 * 3. <code>AbstractAgentRunningModule</code>或者<code>AbstractAgentSubModule</code>子类
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AfterExecute {
    int order() default 0;
}