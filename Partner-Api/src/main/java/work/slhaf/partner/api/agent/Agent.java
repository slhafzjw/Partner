package work.slhaf.partner.api.agent;

import work.slhaf.partner.api.agent.factory.AgentRegisterFactory;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigManager;
import work.slhaf.partner.api.agent.runtime.exception.AgentExceptionCallback;
import work.slhaf.partner.api.agent.runtime.exception.AgentLaunchFailedException;
import work.slhaf.partner.api.agent.runtime.exception.GlobalExceptionHandler;
import work.slhaf.partner.api.agent.runtime.interaction.AgentGateway;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Agent {

    public static AgentGatewayStep newAgent(Class<?> clazz) {
        if (clazz == null) {
            throw new AgentLaunchFailedException("Agent class 和 interaction flow context 不能为 null");
        }
        return new AgentApp(clazz);
    }

    public interface AgentGatewayStep {
        AgentStep setGateway(AgentGateway gateway);
    }

    public interface AgentStep {
        AgentStep addBeforeLaunchRunners(Runnable... runners);

        AgentStep addAfterLaunchRunners(Runnable... runners);

        AgentStep setAgentConfigManager(AgentConfigManager agentConfigManager);

        AgentStep setAgentExceptionCallback(AgentExceptionCallback agentExceptionCallback);

        AgentStep addScanPackage(String packageName);

        AgentStep addScanDir(String externalPackagePath);

        void launch();
    }


    public static class AgentApp implements AgentStep, AgentGatewayStep {

        private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        private final List<Runnable> beforeLaunchRunners = new ArrayList<>();
        private final List<Runnable> afterLaunchRunners = new ArrayList<>();
        private AgentGateway gateway;
        private final Class<?> applicationClass;

        private AgentApp(Class<?> clazz) {
            this.applicationClass = clazz;
        }

        @Override
        public AgentStep setGateway(AgentGateway gateway) {
            this.gateway = gateway;
            return this;
        }

        @Override
        public AgentStep addBeforeLaunchRunners(Runnable... runners) {
            this.beforeLaunchRunners.addAll(List.of(runners));
            return this;
        }

        @Override
        public AgentStep addAfterLaunchRunners(Runnable... runners) {
            this.afterLaunchRunners.addAll(List.of(runners));
            return this;
        }

        @Override
        public AgentStep setAgentConfigManager(AgentConfigManager agentConfigManager) {
            AgentConfigManager.setINSTANCE(agentConfigManager);
            return this;
        }

        @Override
        public AgentStep setAgentExceptionCallback(AgentExceptionCallback agentExceptionCallback) {
            GlobalExceptionHandler.setExceptionCallback(agentExceptionCallback);
            return this;
        }

        @Override
        public AgentStep addScanPackage(String packageName) {
            AgentRegisterFactory.addScanPackage(packageName);
            return this;
        }

        @Override
        public AgentStep addScanDir(String externalPackagePath) {
            AgentRegisterFactory.addScanDir(externalPackagePath);
            return this;
        }

        @Override
        public void launch() {
            launchRunners(beforeLaunchRunners);
            AgentRegisterFactory.launch(applicationClass.getPackageName());
            executorService.execute(() -> gateway.launch());
            launchRunners(afterLaunchRunners);
        }

        private void launchRunners(List<Runnable> runners) {
            for (Runnable runner : runners) {
                executorService.execute(runner);
            }
        }
    }

}
