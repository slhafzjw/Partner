package work.slhaf.partner.api.agent.factory.module.annotation;

import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityHolder;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@CapabilityHolder
public @interface AgentSubModule {
}
