package work.slhaf.partner.api.agent.factory.module.annotation;

import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityHolder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@CapabilityHolder
public @interface AgentSubModule {
}
