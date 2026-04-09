package work.slhaf.partner.framework.agent;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.framework.agent.config.ConfigCenter;
import work.slhaf.partner.framework.agent.exception.AgentLaunchFailedException;
import work.slhaf.partner.framework.agent.factory.AgentRegisterFactory;
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

    public static AgentStep newAgent(Class<?> clazz) {
        if (clazz == null) {
            throw new AgentLaunchFailedException("Agent class 和 interaction flow context 不能为 null");
        }
        return new AgentApp(clazz);
    }

    public interface AgentStep {
        AgentStep addGatewayRegistration(AgentGatewayRegistration... registrations);

        void launch();
    }

    public static class AgentApp implements AgentStep {

        private final Class<?> applicationClass;
        private final Set<AgentGatewayRegistration> gatewayRegistrations = new LinkedHashSet<>();

        private AgentApp(Class<?> clazz) {
            this.applicationClass = clazz;
        }

        @Override
        public AgentStep addGatewayRegistration(AgentGatewayRegistration... registrations) {
            this.gatewayRegistrations.addAll(Set.of(registrations));
            return this;
        }

        @Override
        public void launch() {
            try {
                // Keep startup order explicit so registries are ready before component scanning.
                StateCenter.INSTANCE.toString();
                ModelRuntimeRegistry.INSTANCE.register();
                AgentGatewayRegistry.INSTANCE.register();
                for (AgentGatewayRegistration registration : gatewayRegistrations) {
                    registration.register();
                }
                Path externalModuleDir = ConfigCenter.INSTANCE.getPaths().getResourcesDir().resolve("module");
                AgentRegisterFactory.addScanDir(externalModuleDir.toString());
                AgentRegisterFactory.launch(applicationClass.getPackageName());
                ConfigCenter.INSTANCE.initAll();
                ConfigCenter.INSTANCE.start();
            } catch (Exception e) {
                throw new AgentLaunchFailedException("Agent 启动失败", e);
            }
        }
    }

}
