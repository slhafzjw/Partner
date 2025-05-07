package memory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadPoolTest {
    public static void main(String[] args) throws InterruptedException {
        testExecutor(Executors.newVirtualThreadPerTaskExecutor());

//        Thread.sleep(2000); // 等待系统输出稳定

//        testExecutor("普通线程池", Executors.newFixedThreadPool(100));
    }

    private static void testExecutor(ExecutorService es) throws InterruptedException {
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100000; i++) {
            es.submit(() -> {
                Thread.sleep(1000);
                return 0;
            });
        }

        es.shutdown();
        if (es.awaitTermination(5, TimeUnit.MINUTES)) {
            long end = System.currentTimeMillis();
            System.out.println("虚拟线程" + "耗时：" + (end - start));
        } else {
            System.err.println("虚拟线程" + "未能在规定时间内完成所有任务");
        }
    }
}
