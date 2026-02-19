package work.slhaf.partner.api.agent.factory.capability.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Core的协调类，该注解的实现类中如果存在任何{@link CapabilityCore}实例的引用，都将被自动注入
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CoordinateManager {
}
