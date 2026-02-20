package work.slhaf.partner.common.monitor;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.common.thread.InteractionThreadPoolExecutor;

@Slf4j
public class DebugMonitor {

    private static DebugMonitor debugMonitor;
    private InteractionThreadPoolExecutor executor;

    public static void initialize() {
        debugMonitor = new DebugMonitor();
        debugMonitor.executor = InteractionThreadPoolExecutor.getInstance();
        debugMonitor.runMonitor();
    }

    public static DebugMonitor getInstance() {
        if (debugMonitor == null) {
            initialize();
        }
        return debugMonitor;
    }

    private void runMonitor() {
        executor.execute(() -> {
            while (true) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    log.error("监测线程报错?");
                }
            }
        });
    }
}
