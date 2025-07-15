package work.slhaf.demo.capability.interfaces;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 当`@Capability`所注接口中，如果存在方法需要协调多个Core服务的调用，可以通过该注解进行排除
 * value值为方法对应标识，需与协调实现处的方法标识保持一致
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToCoordinated {
}
