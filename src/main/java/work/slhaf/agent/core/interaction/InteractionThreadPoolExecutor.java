package work.slhaf.agent.core.interaction;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class InteractionThreadPoolExecutor extends ThreadPoolExecutor {

    private static InteractionThreadPoolExecutor interactionThreadPoolExecutor;

    private InteractionThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public static InteractionThreadPoolExecutor getInstance() {
        if (interactionThreadPoolExecutor == null) {
            interactionThreadPoolExecutor = new InteractionThreadPoolExecutor(
                    8,
                    24,
                    60,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(50)
            );
        }
        return interactionThreadPoolExecutor;
    }
}
