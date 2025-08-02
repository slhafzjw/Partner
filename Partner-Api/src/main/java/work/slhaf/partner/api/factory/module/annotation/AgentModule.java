package work.slhaf.partner.api.factory.module.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于注解执行模块
 */
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
