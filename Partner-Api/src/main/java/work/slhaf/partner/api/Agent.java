package work.slhaf.partner.api;

import work.slhaf.partner.api.entity.AgentContext;
import work.slhaf.partner.api.factory.AgentRegisterFactory;
import work.slhaf.partner.api.flow.AgentInteraction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent启动类
 */
public class Agent {

    private static final List<Runnable> runners = new ArrayList<>();

    public static void run(Class<?> clazz) {
        AgentContext context = AgentRegisterFactory.launch(clazz.getPackage().getName());
        AgentInteraction.launch(context);
        launchRunners();
    }

    private static void launchRunners() {
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        for (Runnable runner : runners) {
            executorService.execute(runner);
        }
        executorService.close();
    }

    public static void addRunner(Runnable runnable) {
        runners.add(runnable);
    }
}
