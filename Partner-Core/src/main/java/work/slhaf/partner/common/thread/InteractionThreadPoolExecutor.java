package work.slhaf.partner.common.thread;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class InteractionThreadPoolExecutor {

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
            List<Future<T>> futures = executorService.invokeAll(tasks);
            for (Future<T> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public <T> List<T> invokeAllAndReturn(List<Callable<T>> tasks) {
        try {
            List<Future<T>> futures = executorService.invokeAll(tasks);
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public void execute(Runnable runnable) {
        executorService.execute(runnable);
    }
}
