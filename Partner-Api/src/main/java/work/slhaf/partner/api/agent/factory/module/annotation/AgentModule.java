package work.slhaf.partner.api.agent.factory.module.annotation;


import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityHolder;

import java.lang.annotation.*;

/**
 * 用于注解执行模块
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@CapabilityHolder
@Inherited
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
