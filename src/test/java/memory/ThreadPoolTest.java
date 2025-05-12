package memory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadPoolTest {

//    @Test
    public void testExecutor() throws InterruptedException {
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int finalI = i;
            tasks.add(() -> {
                System.out.println("开始: " + finalI);
                Thread.sleep(5000);
                System.out.println("结束: " + finalI);
                return null;
            });
        }

        Executors.newVirtualThreadPerTaskExecutor().invokeAll(tasks, 10, TimeUnit.SECONDS);

        System.out.println("hello");
    }
}
