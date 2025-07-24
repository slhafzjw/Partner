package work.slhaf.partner.api.factory.capability.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于注解能力接口，需要与`@CapabilityCore`对应的`value`一致
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Capability {
    String value();
}
