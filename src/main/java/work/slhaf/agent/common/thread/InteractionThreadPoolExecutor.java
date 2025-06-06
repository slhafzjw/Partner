package work.slhaf.agent.common.thread;

import lombok.Getter;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Getter
public class InteractionThreadPoolExecutor  {

    private static InteractionThreadPoolExecutor interactionThreadPoolExecutor;

    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    public static InteractionThreadPoolExecutor getInstance() {
        if (interactionThreadPoolExecutor == null) {
            interactionThreadPoolExecutor = new InteractionThreadPoolExecutor();
        }
        return interactionThreadPoolExecutor;
    }


    public <T> void invokeAll(List<Callable<T>> tasks, int time, TimeUnit timeUnit) {
        try {
            executorService.invokeAll(tasks, time, timeUnit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    
    public <T> void invokeAll(List<Callable<T>> tasks) {
        try {
            executorService.invokeAll(tasks);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void execute(Runnable runnable) {
        executorService.execute(runnable);
    }
}
