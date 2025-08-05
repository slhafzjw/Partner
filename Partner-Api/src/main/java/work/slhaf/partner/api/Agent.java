package work.slhaf.partner.api;

import work.slhaf.partner.api.exception.AgentLaunchFailedException;
import work.slhaf.partner.api.factory.AgentRegisterFactory;
import work.slhaf.partner.api.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.flow.AgentInteraction;
import work.slhaf.partner.api.flow.entity.InteractionFlowContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent启动类
 */
public class Agent {

    private final List<Runnable> runners = new ArrayList<>();
    private final Class<?> applicationClass;
    private final InteractionFlowContext interactionContext;

    private Agent(Class<?> clazz, InteractionFlowContext interactionContext) {
        this.applicationClass = clazz;
        this.interactionContext = interactionContext;
    }

    public static Agent newAgent(Class<?> clazz, InteractionFlowContext interactionContext) {
        if (clazz == null || interactionContext == null) {
            throw new AgentLaunchFailedException("Agent class 和 interaction flow context 不能为 null");
        }
        return new Agent(clazz, interactionContext);
    }

    public void run() {
        List<MetaModule> moduleList = AgentRegisterFactory.launch(applicationClass.getPackage().getName());
        AgentInteraction.launch(moduleList, interactionContext);
        launchRunners();
    }


    private void launchRunners() {
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        for (Runnable runner : runners) {
            executorService.execute(runner);
        }
    }

    public Agent addRunners(Runnable... runnable) {
        runners.addAll(List.of(runnable));
        return this;
    }
}
