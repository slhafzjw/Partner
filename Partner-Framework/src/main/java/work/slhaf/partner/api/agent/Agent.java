package work.slhaf.partner.api.agent;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.AgentRegisterFactory;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigLoader;
import work.slhaf.partner.api.agent.runtime.exception.AgentExceptionCallback;
import work.slhaf.partner.api.agent.runtime.exception.AgentLaunchFailedException;
import work.slhaf.partner.api.agent.runtime.exception.GlobalExceptionHandler;
import work.slhaf.partner.api.agent.runtime.interaction.AgentGateway;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <h2>Agent 启动入口</h2>
 * 详细启动流程请参阅{@link AgentRegisterFactory}
 */
@Slf4j
public final class Agent {

    public static AgentConfigManagerStep newAgent(Class<?> clazz) {
        if (clazz == null) {
            throw new AgentLaunchFailedException("Agent class 和 interaction flow context 不能为 null");
        }
        return new AgentApp(clazz);
    }

    public interface AgentConfigManagerStep {
        AgentGatewayStep setAgentConfigManager(Class<? extends AgentConfigLoader> agentConfigManager);
    }

    public interface AgentGatewayStep {
        AgentStep setGateway(Class<? extends AgentGateway> gateway);
    }

    public interface AgentStep {
        AgentStep addBeforeLaunchRunners(Runnable... runners);

        AgentStep addAfterLaunchRunners(Runnable... runners);

        AgentStep setAgentExceptionCallback(Class<? extends AgentExceptionCallback> agentExceptionCallback);

        AgentStep addScanPackage(String packageName);

        AgentStep addScanDir(String externalPackagePath);

        void launch();
    }


    public static class AgentApp implements AgentStep, AgentGatewayStep, AgentConfigManagerStep {

        private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        private final List<Runnable> beforeLaunchRunners = new ArrayList<>();
        private final List<Runnable> afterLaunchRunners = new ArrayList<>();
        private final Class<?> applicationClass;
        private final CountDownLatch latch = new CountDownLatch(1);
        private AgentGateway gateway;
        private Class<? extends AgentConfigLoader> agentConfigManagerClass;
        private Class<? extends AgentGateway> gatewayClass;
        private Class<? extends AgentExceptionCallback> agentExceptionCallbackClass;

        private AgentApp(Class<?> clazz) {
            this.applicationClass = clazz;
        }

        @Override
        public AgentStep setGateway(Class<? extends AgentGateway> gateway) {
            this.gatewayClass = gateway;
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
        public AgentGatewayStep setAgentConfigManager(Class<? extends AgentConfigLoader> agentConfigManager) {
            this.agentConfigManagerClass = agentConfigManager;
            return this;
        }

        @Override
        public AgentStep setAgentExceptionCallback(Class<? extends AgentExceptionCallback> agentExceptionCallback) {
            agentExceptionCallbackClass = agentExceptionCallback;
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
            beforeLaunch();
            AgentRegisterFactory.launch(applicationClass.getPackageName());
            afterLaunch();
        }

        private void afterLaunch() {
            try {
                this.gateway = gatewayClass.getDeclaredConstructor().newInstance();
                executorService.execute(() -> {
                    gateway.launch();
                    latch.countDown();
                    log.info("Gateway 启动完毕: {}", gatewayClass.getSimpleName());
                });
                latch.await();
                launchRunners(afterLaunchRunners);
                log.info("后置任务启动完毕");
            } catch (Exception e) {
                throw new AgentLaunchFailedException("Agent 后置任务启动失败", e);
            }
        }

        private void beforeLaunch() {
            try {
                AgentConfigLoader.setINSTANCE(agentConfigManagerClass.getDeclaredConstructor().newInstance());
                log.info("配置管理器设置完毕: {}", agentConfigManagerClass.getSimpleName());
                GlobalExceptionHandler.setExceptionCallback(agentExceptionCallbackClass.getDeclaredConstructor().newInstance());
                log.info("异常处理回调设置完毕: {}", agentExceptionCallbackClass.getSimpleName());
                launchRunners(beforeLaunchRunners);
                log.info("前置任务启动完毕");
            } catch (Exception e) {
                throw new AgentLaunchFailedException("Agent 前置任务启动失败", e);
            }
        }

        private void launchRunners(List<Runnable> runners) {
            for (Runnable runner : runners) {
                executorService.execute(runner);
            }
        }
    }

}
