package work.slhaf.partner.framework.common.support;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import work.slhaf.partner.framework.agent.common.support.DirectoryWatchSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

class DirectoryWatchSupportTest {

    @Test
    void testWatchDepthRejectsInvalidValue(@TempDir Path tempDir) throws IOException {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            DirectoryWatchSupport.Context context = new DirectoryWatchSupport.Context(tempDir);
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> new DirectoryWatchSupport(context, executor, -2, null));
        }
    }

    @Test
    void testWatchDepthZeroOnlyWatchesRoot(@TempDir Path tempDir) throws Exception {
        Path childDir = Files.createDirectories(tempDir.resolve("child"));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             WatchHarness harness = createWatchSupport(tempDir, executor, 0)) {
            harness.watchSupport().start();

            Files.writeString(tempDir.resolve("root.txt"), "root");
            waitForCondition(() -> harness.events().contains("root.txt"), 2000);

            Files.writeString(childDir.resolve("child.txt"), "child");
            Thread.sleep(300);

            Assertions.assertTrue(harness.events().contains("root.txt"));
            Assertions.assertFalse(harness.events().contains("child/child.txt"));
        }
    }

    @Test
    void testWatchDepthOneWatchesDirectChildrenOnly(@TempDir Path tempDir) throws Exception {
        Path childDir = Files.createDirectories(tempDir.resolve("child"));
        Path grandChildDir = Files.createDirectories(childDir.resolve("grandchild"));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             WatchHarness harness = createWatchSupport(tempDir, executor, 1)) {
            harness.watchSupport().start();

            Files.writeString(childDir.resolve("child.txt"), "child");
            waitForCondition(() -> harness.events().contains("child/child.txt"), 2000);

            Files.writeString(grandChildDir.resolve("deep.txt"), "deep");
            Thread.sleep(300);

            Assertions.assertTrue(harness.events().contains("child/child.txt"));
            Assertions.assertFalse(harness.events().contains("child/grandchild/deep.txt"));
        }
    }

    @Test
    void testWatchDepthNegativeOneWatchesAllDescendants(@TempDir Path tempDir) throws Exception {
        Path grandChildDir = Files.createDirectories(tempDir.resolve("child").resolve("grandchild"));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             WatchHarness harness = createWatchSupport(tempDir, executor, -1)) {
            harness.watchSupport().start();

            Files.writeString(grandChildDir.resolve("deep.txt"), "deep");
            waitForCondition(() -> harness.events().contains("child/grandchild/deep.txt"), 2000);

            Assertions.assertTrue(harness.events().contains("child/grandchild/deep.txt"));
        }
    }

    @Test
    void testRegistersNewDirectoriesUpToConfiguredDepth(@TempDir Path tempDir) throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             WatchHarness harness = createWatchSupport(tempDir, executor, 1)) {
            harness.watchSupport().start();

            Path childDir = Files.createDirectories(tempDir.resolve("child"));
            waitForCondition(() -> harness.watchSupport().isWatching(childDir), 2000);

            Files.writeString(childDir.resolve("child.txt"), "child");
            waitForCondition(() -> harness.events().contains("child/child.txt"), 2000);

            Assertions.assertTrue(harness.events().contains("child/child.txt"));
        }
    }

    @Test
    void testReRegistersRootAfterRecreate(@TempDir Path tempDir) throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             WatchHarness harness = createWatchSupport(tempDir, executor, 0)) {
            harness.watchSupport().start();

            deleteDirectory(tempDir);
            waitForCondition(() -> !harness.watchSupport().isWatching(tempDir), 2000);
            Files.createDirectories(tempDir);

            waitForCondition(() -> harness.watchSupport().isWatching(tempDir), 2000);

            Files.writeString(tempDir.resolve("recreated.txt"), "ok");
            waitForCondition(() -> harness.events().contains("recreated.txt"), 3000);

            Assertions.assertTrue(harness.events().contains("recreated.txt"));
        }
    }

    private WatchHarness createWatchSupport(Path root, ExecutorService executor, int watchDepth) throws IOException {
        DirectoryWatchSupport watchSupport = new DirectoryWatchSupport(new DirectoryWatchSupport.Context(root), executor, watchDepth, null);
        List<String> events = new CopyOnWriteArrayList<>();
        watchSupport.onCreate((thisDir, context) -> record(root, context, events));
        watchSupport.onModify((thisDir, context) -> record(root, context, events));
        return new WatchHarness(watchSupport, events);
    }

    private void record(Path root, Path context, List<String> events) {
        if (context == null || Files.isDirectory(context)) {
            return;
        }
        events.add(root.relativize(context).toString().replace('\\', '/'));
    }

    private void waitForCondition(BooleanSupplier supplier, long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (!supplier.getAsBoolean()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                break;
            }
            Thread.sleep(50);
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private record WatchHarness(DirectoryWatchSupport watchSupport,
                                List<String> events) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            watchSupport.close();
        }
    }
}
