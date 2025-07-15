package work.slhaf.demo.capability.interfaces;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于标注协调方法，`value`值需与对应的`@ToCoordinated`保持一致
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Coordinated {
    String capability();
}
