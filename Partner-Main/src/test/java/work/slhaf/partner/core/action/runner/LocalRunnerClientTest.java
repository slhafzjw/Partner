package work.slhaf.partner.core.action.runner;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.entity.MetaActionType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

@SuppressWarnings("LoggingSimilarMessage")
@Slf4j
public class LocalRunnerClientTest {

    private static void writeRunFile(Path actionDir) throws IOException {
        Path runPath = actionDir.resolve("run.py");
        log.debug("写入路径: {}", runPath);
        Files.writeString(runPath, "print('ok')\n");
    }

    private static @NotNull MetaAction buildTmpMetaAction() {
        MetaAction metaAction = new MetaAction();
        metaAction.setIo(false);
        metaAction.setName("hello_world");
        metaAction.setParams(Map.of("name", "origin_run"));
        metaAction.setType(MetaActionType.ORIGIN);
        metaAction.setLocation("/home/slhaf/Projects/IdeaProjects/Projects/Partner/Partner-Main/src/test/java/resources/action/tmp/hello_world.py");
        return metaAction;
    }

    private static void writeDescJson(Path actionDir, String description) throws IOException {
        Path descPath = actionDir.resolve("desc.json");
        log.debug("写入路径: {}", descPath);
        String json = "{\n"
                + "  \"io\": false,\n"
                + "  \"params\": {},\n"
                + "  \"description\": \"" + description + "\",\n"
                + "  \"tags\": [],\n"
                + "  \"preActions\": [],\n"
                + "  \"postActions\": [],\n"
                + "  \"strictDependencies\": false,\n"
                + "  \"responseSchema\": {}\n"
                + "}\n";
        Files.writeString(descPath, json);
    }

    private static void waitForCondition(BooleanSupplier supplier, long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (!supplier.getAsBoolean()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                break;
            }
            Thread.sleep(50);
        }
    }

    @Test
    void testRunOrigin(@TempDir Path tempDir) {
        LocalRunnerClient runnerClient =
                new LocalRunnerClient(
                        new ConcurrentHashMap<>(),
                        Executors.newVirtualThreadPerTaskExecutor(),
                        tempDir.toString()
                );

        MetaAction metaAction = buildTmpMetaAction();
        RunnerClient.RunnerResponse runnerResponse = runnerClient.doRun(metaAction);
        System.out.println(runnerResponse.getData());
    }

    @Test
    void testWatch(@TempDir Path tempDir) {
        LocalRunnerClient runnerClient =
                new LocalRunnerClient(
                        new ConcurrentHashMap<>(),
                        Executors.newVirtualThreadPerTaskExecutor(),
                        tempDir.toString()
                );
        // 直接等待输入然后尝试触发各种文件监听事件即可
        System.out.println("Press any key to continue...");
        Scanner scanner = new Scanner(System.in);
        scanner.next();
    }

    @Test
    void testDynamicWatchCreateModifyDelete(@TempDir Path tempDir) throws IOException, InterruptedException {
        ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        LocalRunnerClient client = new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

        try {
            Path dynamicRoot = tempDir.resolve("action").resolve("dynamic");
            Path actionDir = dynamicRoot.resolve("demo_action");
            Files.createDirectories(actionDir);
            Thread.sleep(100);

            writeRunFile(actionDir);
            writeDescJson(actionDir, "demo action");
            waitForCondition(() -> existedMetaActions.containsKey("local::demo_action"), 2000);
            Assertions.assertTrue(existedMetaActions.containsKey("local::demo_action"));

            // 触发一次 modify，确保监听线程能够捕捉到完整的 action 结构
            writeDescJson(actionDir, "demo action updated");

            waitForCondition(() -> existedMetaActions.containsKey("local::demo_action"), 2000);
            Assertions.assertTrue(existedMetaActions.containsKey("local::demo_action"));

            Files.deleteIfExists(actionDir.resolve("run.py"));
            waitForCondition(() -> !existedMetaActions.containsKey("local::demo_action"), 2000);
            Assertions.assertFalse(existedMetaActions.containsKey("local::demo_action"));
        } finally {
            executor.shutdownNow();
        }
    }
}
