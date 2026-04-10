package work.slhaf.partner.framework.agent;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.framework.agent.config.ConfigCenter;
import work.slhaf.partner.framework.agent.exception.AgentStartupException;
import work.slhaf.partner.framework.agent.exception.ExceptionReporter;
import work.slhaf.partner.framework.agent.exception.ExceptionReporterHandler;
import work.slhaf.partner.framework.agent.factory.AgentRegisterFactory;
import work.slhaf.partner.framework.agent.factory.component.annotation.AgentComponent;
import work.slhaf.partner.framework.agent.factory.context.AgentContext;
import work.slhaf.partner.framework.agent.interaction.AgentGatewayRegistration;
import work.slhaf.partner.framework.agent.interaction.AgentGatewayRegistry;
import work.slhaf.partner.framework.agent.model.ModelRuntimeRegistry;
import work.slhaf.partner.framework.agent.state.StateCenter;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * <h2>Agent 启动入口</h2>
 * 详细启动流程请参阅{@link AgentRegisterFactory}
 */
@Slf4j
public final class Agent {

    public static AgentApp newAgent(@NonNull Class<?> clazz) {
        return new AgentApp(clazz);
    }

    public static class AgentApp {

        private final Class<?> applicationClass;
        private final Set<AgentGatewayRegistration> gatewayRegistrations = new LinkedHashSet<>();
        private final Set<ExceptionReporter> exceptionReporters = new LinkedHashSet<>();
        private final Set<LifecycleHook> preShutdownHooks = new LinkedHashSet<>();
        private final Set<LifecycleHook> postShutdownHooks = new LinkedHashSet<>();

        private AgentApp(Class<?> clazz) {
            this.applicationClass = clazz;
        }

        public AgentApp addGatewayRegistration(AgentGatewayRegistration... registrations) {
            this.gatewayRegistrations.addAll(Set.of(registrations));
            return this;
        }

        public AgentApp addExceptionReporter(ExceptionReporter... exceptionReporters) {
            this.exceptionReporters.addAll(Set.of(exceptionReporters));
            return this;
        }

        public AgentApp addPreShutdownHook(String name, Runnable action) {
            return addPreShutdownHook(name, 0, action);
        }

        public AgentApp addPreShutdownHook(String name, int order, Runnable action) {
            this.preShutdownHooks.add(new LifecycleHook(name, order, action));
            return this;
        }

        public AgentApp addPostShutdownHook(String name, Runnable action) {
            return addPostShutdownHook(name, 0, action);
        }

        public AgentApp addPostShutdownHook(String name, int order, Runnable action) {
            this.postShutdownHooks.add(new LifecycleHook(name, order, action));
            return this;
        }

        public boolean launch() {
            try {
                // Keep startup order explicit so registries are ready before component scanning.
                for (ExceptionReporter exceptionReporter : exceptionReporters) {
                    // AgentComponent will be initialized by factory
                    if (exceptionReporter.getClass().isAnnotationPresent(AgentComponent.class)) {
                        continue;
                    }
                    exceptionReporter.register();
                }
                // Load class
                StateCenter.INSTANCE.toString();
                // Register into config center
                ModelRuntimeRegistry.INSTANCE.register();
                AgentGatewayRegistry.INSTANCE.register();

                for (AgentGatewayRegistration registration : gatewayRegistrations) {
                    registration.register();
                }

                registerShutdownHooks();

                Path externalModuleDir = ConfigCenter.INSTANCE.getPaths().getResourcesDir().resolve("module");
                AgentRegisterFactory.addScanDir(externalModuleDir.toString());
                AgentRegisterFactory.launch(applicationClass.getPackageName());

                // Try to init configurable, and start config listening
                ConfigCenter.INSTANCE.initAll();
                ConfigCenter.INSTANCE.start();
                return true;
            } catch (AgentStartupException e) {
                ExceptionReporterHandler.INSTANCE.report(e);
                return false;
            } catch (Throwable t) {
                AgentStartupException wrapped = new AgentStartupException("Unexpected startup failure", "launcher", t);
                ExceptionReporterHandler.INSTANCE.report(wrapped);
                return false;
            }
        }

        private void registerShutdownHooks() {
            AgentContext.INSTANCE.addPreShutdownHook(
                    "agent-gateway-registry-close",
                    0,
                    AgentGatewayRegistry.INSTANCE::close
            );
            preShutdownHooks.forEach(hook ->
                    AgentContext.INSTANCE.addPreShutdownHook(hook.name(), hook.order(), hook.action())
            );
            AgentContext.INSTANCE.addPostShutdownHook(
                    "state-center-save",
                    0,
                    StateCenter.INSTANCE::save
            );
            AgentContext.INSTANCE.addPostShutdownHook(
                    "config-center-close",
                    100,
                    ConfigCenter.INSTANCE::close
            );
            postShutdownHooks.forEach(hook ->
                    AgentContext.INSTANCE.addPostShutdownHook(hook.name(), hook.order(), hook.action())
            );
        }
    }

    private record LifecycleHook(String name, int order, Runnable action) {
    }

}
