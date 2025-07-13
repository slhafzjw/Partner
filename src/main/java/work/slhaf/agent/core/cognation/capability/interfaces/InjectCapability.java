package work.slhaf.agent.core.cognation.capability.interfaces;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
public @interface InjectCapability {
    String value();
}
