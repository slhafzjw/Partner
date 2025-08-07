package work.slhaf.partner.api.agent.factory.module.annotation;


import java.lang.annotation.*;

/**
 * 用于注解执行模块
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentModule {

    /**
     * 模块名称
     */
    String name();

    /**
     * 模块执行顺序，数字越小执行越靠前
     */
    int order();
}
