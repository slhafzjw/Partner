package work.slhaf.partner.api.agent.factory.module.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@AgentRunningModule(name = "core", order = 5)
public @interface CoreModule {
}
