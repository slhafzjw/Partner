package work.slhaf.partner.framework.agent;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.reflections.util.ClasspathHelper;
import work.slhaf.partner.framework.agent.config.ConfigCenter;
import work.slhaf.partner.framework.agent.config.Configurable;
import work.slhaf.partner.framework.agent.exception.AgentStartupException;
import work.slhaf.partner.framework.agent.exception.ExceptionReporter;
import work.slhaf.partner.framework.agent.exception.ExceptionReporterHandler;
import work.slhaf.partner.framework.agent.factory.AgentRegisterFactory;
import work.slhaf.partner.framework.agent.factory.context.AgentContext;
import work.slhaf.partner.framework.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.framework.agent.interaction.AgentGatewayRegistration;
import work.slhaf.partner.framework.agent.interaction.AgentGatewayRegistry;
import work.slhaf.partner.framework.agent.log.LogAdviceProvider;
import work.slhaf.partner.framework.agent.log.TraceSinkRegistry;
import work.slhaf.partner.framework.agent.model.ModelRuntimeRegistry;
import work.slhaf.partner.framework.agent.state.StateCenter;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

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

        private final Set<String> scanPackages = new LinkedHashSet<>();
        private final Set<String> scanDirs = new LinkedHashSet<>();
        private final Set<AgentGatewayRegistration> gatewayRegistrations = new LinkedHashSet<>();
        private final Set<ExceptionReporter> exceptionReporters = new LinkedHashSet<>();
        private final Set<Configurable> configurables = new LinkedHashSet<>();
        private final Set<LifecycleHook> preShutdownHooks = new LinkedHashSet<>();
        private final Set<LifecycleHook> postShutdownHooks = new LinkedHashSet<>();

        private AgentApp(Class<?> clazz) {
            this.scanPackages.add(clazz.getPackageName());
        }

        private void addScanPackage(String packageName) {
            if (packageName != null && !packageName.isBlank()) {
                this.scanPackages.add(packageName);
            }
        }

        private void addScanDir(String scanDir) {
            if (scanDir != null && !scanDir.isBlank()) {
                this.scanDirs.add(scanDir);
            }
        }

        private void addGatewayRegistration(AgentGatewayRegistration... registrations) {
            this.gatewayRegistrations.addAll(Set.of(registrations));
        }

        private void addConfigurable(Configurable configurable) {
            this.configurables.add(configurable);
        }

        private void addExceptionReporter(ExceptionReporter... exceptionReporters) {
            this.exceptionReporters.addAll(Set.of(exceptionReporters));
        }

        private void addPreShutdownHook(String name, Runnable action) {
            addPreShutdownHook(name, 0, action);
        }

        private void addPreShutdownHook(String name, int order, Runnable action) {
            this.preShutdownHooks.add(new LifecycleHook(name, order, action));
        }

        private void addPostShutdownHook(String name, Runnable action) {
            addPostShutdownHook(name, 0, action);
        }

        private void addPostShutdownHook(String name, int order, Runnable action) {
            this.postShutdownHooks.add(new LifecycleHook(name, order, action));
        }

        public boolean launch() {
            try {
                // load class
                ConfigCenter.INSTANCE.toString();
                StateCenter.INSTANCE.toString();

                Path externalModuleDir = ConfigCenter.INSTANCE.getPaths().getResourcesDir().resolve("module");
                addScanDir(externalModuleDir.toString());

                AgentRegisterContext bootstrapContext = buildRegisterContext();
                runBootstraps(bootstrapContext);
                AgentRegisterContext registerContext = buildRegisterContext();

                // Keep startup order explicit so registries are ready before component scanning.
                for (ExceptionReporter exceptionReporter : exceptionReporters) {
                    exceptionReporter.register();
                }

                // Register into config center
                LogAdviceProvider.INSTANCE.register();
                ModelRuntimeRegistry.INSTANCE.register();
                AgentGatewayRegistry.INSTANCE.register();
                for (Configurable configurable : configurables) {
                    configurable.register();
                }

                for (AgentGatewayRegistration registration : gatewayRegistrations) {
                    registration.register();
                }

                registerShutdownHooks();

                AgentRegisterFactory.launch(registerContext);

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

        private AgentRegisterContext buildRegisterContext() {
            return new AgentRegisterContext(new ArrayList<>(buildScanUrls()));
        }

        private Set<URL> buildScanUrls() {
            Set<URL> urls = new LinkedHashSet<>();
            for (String packageName : scanPackages) {
                urls.addAll(ClasspathHelper.forPackage(packageName));
            }
            for (String scanDir : scanDirs) {
                urls.addAll(scanDirToUrls(scanDir));
            }
            return urls;
        }

        private Set<URL> scanDirToUrls(String scanDir) {
            Set<URL> urls = new LinkedHashSet<>();
            File file = new File(scanDir);
            if (!file.exists() || !file.isDirectory()) {
                return urls;
            }
            try {
                File[] files = file.listFiles();
                if (files == null) {
                    return urls;
                }
                for (File item : files) {
                    if (item.getName().endsWith(".jar")) {
                        urls.add(item.toURI().toURL());
                    }
                }
                return urls;
            } catch (Exception e) {
                throw new AgentStartupException("Failed to load scan dir URLs from: " + scanDir, "agent-bootstrap", e);
            }
        }

        private void runBootstraps(AgentRegisterContext context) {
            context.getReflections().getSubTypesOf(AgentBootstrap.class).stream()
                    .filter(this::isConcreteBootstrap)
                    .map(this::instantiateBootstrap)
                    .sorted(Comparator.comparingInt(AgentBootstrap::order))
                    .forEach(AgentBootstrap::bootstrap);
        }

        private boolean isConcreteBootstrap(Class<? extends AgentBootstrap> bootstrapClass) {
            int modifiers = bootstrapClass.getModifiers();
            return !bootstrapClass.isInterface()
                    && !bootstrapClass.isAnnotation()
                    && !bootstrapClass.isEnum()
                    && !bootstrapClass.isArray()
                    && !bootstrapClass.isPrimitive()
                    && !Modifier.isAbstract(modifiers)
                    && !bootstrapClass.isSynthetic()
                    && !bootstrapClass.isAnonymousClass()
                    && !bootstrapClass.isLocalClass();
        }

        private AgentBootstrap instantiateBootstrap(Class<? extends AgentBootstrap> bootstrapClass) {
            try {
                Constructor<? extends AgentBootstrap> constructor = bootstrapClass.getDeclaredConstructor(AgentApp.class);
                constructor.setAccessible(true);
                return constructor.newInstance(this);
            } catch (Exception e) {
                throw new AgentStartupException(
                        "Failed to instantiate AgentBootstrap: " + bootstrapClass.getName(),
                        "agent-bootstrap",
                        e
                );
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
                    "trace-sink-registry-close",
                    90,
                    TraceSinkRegistry.INSTANCE::close
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

    public static abstract class AgentBootstrap {

        private final AgentApp agentApp;

        protected AgentBootstrap(AgentApp agentApp) {
            this.agentApp = Objects.requireNonNull(agentApp, "agentApp");
        }

        public int order() {
            return 0;
        }

        protected abstract void bootstrap();

        protected final void addScanPackage(String packageName) {
            agentApp.addScanPackage(packageName);
        }

        protected final void addScanDir(String scanDir) {
            agentApp.addScanDir(scanDir);
        }

        protected final void addPreShutdownHook(String name, Runnable action) {
            agentApp.addPreShutdownHook(name, action);
        }

        protected final void addPreShutdownHook(String name, int order, Runnable action) {
            agentApp.addPreShutdownHook(name, order, action);
        }

        protected final void addPostShutdownHook(String name, Runnable action) {
            agentApp.addPostShutdownHook(name, action);
        }

        protected final void addPostShutdownHook(String name, int order, Runnable action) {
            agentApp.addPostShutdownHook(name, order, action);
        }

        protected final void addExceptionReporter(ExceptionReporter... exceptionReporters) {
            agentApp.addExceptionReporter(exceptionReporters);
        }

        protected final void addConfigurable(Configurable configurable) {
            agentApp.addConfigurable(configurable);
        }

        protected final void addGatewayRegistration(AgentGatewayRegistration... gatewayRegistrations) {
            agentApp.addGatewayRegistration(gatewayRegistrations);
        }
    }

}
