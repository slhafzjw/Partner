package work.slhaf.partner.framework.agent;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.framework.agent.config.ConfigCenter;
import work.slhaf.partner.framework.agent.exception.deprecated.AgentLaunchFailedException;
import work.slhaf.partner.framework.agent.factory.AgentRegisterFactory;
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
        private final Set<LifecycleHook> preShutdownHooks = new LinkedHashSet<>();
        private final Set<LifecycleHook> postShutdownHooks = new LinkedHashSet<>();

        private AgentApp(Class<?> clazz) {
            this.applicationClass = clazz;
        }

        public AgentApp addGatewayRegistration(AgentGatewayRegistration... registrations) {
            this.gatewayRegistrations.addAll(Set.of(registrations));
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

        public void launch() {
            try {
                // Keep startup order explicit so registries are ready before component scanning.
                StateCenter.INSTANCE.toString();
                ModelRuntimeRegistry.INSTANCE.register();
                AgentGatewayRegistry.INSTANCE.register();
                for (AgentGatewayRegistration registration : gatewayRegistrations) {
                    registration.register();
                }
                registerShutdownHooks();
                Path externalModuleDir = ConfigCenter.INSTANCE.getPaths().getResourcesDir().resolve("module");
                AgentRegisterFactory.addScanDir(externalModuleDir.toString());
                AgentRegisterFactory.launch(applicationClass.getPackageName());
                ConfigCenter.INSTANCE.initAll();
                ConfigCenter.INSTANCE.start();
            } catch (Exception e) {
                throw new AgentLaunchFailedException("Agent 启动失败", e);
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
