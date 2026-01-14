package work.slhaf.partner.core.action.runner;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static work.slhaf.partner.core.action.runner.LocalRunnerClientTest.Await.waitForCondition;
import static work.slhaf.partner.core.action.runner.LocalRunnerClientTest.Common.*;
import static work.slhaf.partner.core.action.runner.LocalRunnerClientTest.Fs.*;

@Slf4j
public class LocalRunnerClientTest {

    @SuppressWarnings("LoggingSimilarMessage")
    static class Fs {
        static void writeRunFile(Path actionDir) throws IOException {
            Path runPath = actionDir.resolve("run.py");
            log.debug("写入路径: {}", runPath);
            Files.writeString(runPath, "print('ok')\n");
        }

        static void writeInvalidDescJson(Path actionDir) throws IOException {
            Path descPath = actionDir.resolve("desc.json");
            log.debug("写入路径: {}", descPath);
            Files.writeString(descPath, "{ invalid json");
        }

        static void writeDescJson(Path actionDir, String description) throws IOException {
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

        static void writeDescMcpJson(Path descDir, String actionKey, String description) throws IOException {
            Path descPath = descDir.resolve(actionKey + ".desc.json");
            log.debug("写入路径: {}", descPath);
            String json = "{\n"
                    + "  \"io\": true,\n"
                    + "  \"params\": {},\n"
                    + "  \"description\": \"" + description + "\",\n"
                    + "  \"tags\": [\"tag\"],\n"
                    + "  \"preActions\": [\"pre\"],\n"
                    + "  \"postActions\": [\"post\"],\n"
                    + "  \"strictDependencies\": true,\n"
                    + "  \"responseSchema\": {}\n"
                    + "}\n";
            Files.writeString(descPath, json);
        }

        static void writeInvalidDescMcpJson(Path descDir, String actionKey) throws IOException {
            Path descPath = descDir.resolve(actionKey + ".desc.json");
            log.debug("写入路径: {}", descPath);
            Files.writeString(descPath, "{ invalid json");
        }

        @SuppressWarnings("SameParameterValue")
        static void writeDescJsonAtomic(Path actionDir, String description) throws IOException {
            Path descPath = actionDir.resolve("desc.json");
            Path tmpPath = actionDir.resolve("desc.json.tmp");
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
            Files.writeString(tmpPath, json);
            Files.move(tmpPath, descPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }

        static void deleteDirectory(Path dir) throws IOException {
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

        static void writeCommonMcpConfig(Path filePath, String content) throws IOException {
            Files.writeString(filePath, content);
        }

    }

    @SuppressWarnings("BusyWait")
    static class Await {
        static void waitForCondition(BooleanSupplier supplier, long timeoutMs) throws InterruptedException {
            long start = System.currentTimeMillis();
            while (!supplier.getAsBoolean()) {
                if (System.currentTimeMillis() - start > timeoutMs) {
                    break;
                }
                Thread.sleep(50);
            }
        }
    }

    static class Common {
        static MetaActionInfo getMetaActionInfo(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions,
                                                String actionKey) {
            return existedMetaActions.get(actionKey);
        }

        static boolean hasActionKey(ConcurrentHashMap<String, MetaActionInfo> existedMetaActions,
                                    Predicate<String> predicate) {
            return existedMetaActions.keySet().stream().anyMatch(predicate);
        }

        static MetaActionInfo buildMetaActionInfo(String description) {
            MetaActionInfo info = new MetaActionInfo();
            info.setIo(true);
            info.setParams(new HashMap<>());
            info.setDescription(description);
            info.setTags(new ArrayList<>(List.of("tag")));
            info.setPreActions(new ArrayList<>(List.of("pre")));
            info.setPostActions(new ArrayList<>(List.of("post")));
            info.setStrictDependencies(true);
            return info;
        }

        static String buildCommonMcpConfig(String... serverEntries) {
            StringBuilder builder = new StringBuilder();
            builder.append("{\n");
            for (int i = 0; i < serverEntries.length; i++) {
                builder.append(serverEntries[i]);
                if (i < serverEntries.length - 1) {
                    builder.append(",\n");
                }
            }
            builder.append("\n}\n");
            return builder.toString();
        }

        static String buildStdioServerEntry(String id, String packageName) {
            return "  \"" + id + "\": {\n"
                    + "    \"command\": \"npx\",\n"
                    + "    \"args\": [\n"
                    + "      \"-y\",\n"
                    + "      \"" + packageName + "\"\n"
                    + "    ],\n"
                    + "    \"env\": {}\n"
                    + "  }";
        }
    }

    @Nested
    class DynamicMcpTest {

        @Test
        void testDynamicWatchCreateModifyDelete(@TempDir Path tempDir) throws IOException, InterruptedException {
            ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

            try {
                Path dynamicRoot = tempDir.resolve("action").resolve("dynamic");
                Path actionDir = dynamicRoot.resolve("demo_action");
                Files.createDirectories(actionDir);

                Fs.writeRunFile(actionDir);
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

        @Test
        void testDynamicWatchOutOfOrderEvents(@TempDir Path tempDir) throws IOException, InterruptedException {
            ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

            try {
                Path dynamicRoot = tempDir.resolve("action").resolve("dynamic");

                Path actionDir = dynamicRoot.resolve("demo_action_order");
                Files.createDirectories(actionDir);
                writeDescJson(actionDir, "desc first");
                waitForCondition(() -> existedMetaActions.containsKey("local::demo_action_order"), 500);
                Assertions.assertFalse(existedMetaActions.containsKey("local::demo_action_order"));

                writeRunFile(actionDir);
                waitForCondition(() -> existedMetaActions.containsKey("local::demo_action_order"), 2000);
                Assertions.assertTrue(existedMetaActions.containsKey("local::demo_action_order"));

                Path descOnlyDir = dynamicRoot.resolve("demo_action_desc_only");
                Files.createDirectories(descOnlyDir);
                writeDescJson(descOnlyDir, "desc only");
                waitForCondition(() -> existedMetaActions.containsKey("local::demo_action_desc_only"), 500);
                Assertions.assertFalse(existedMetaActions.containsKey("local::demo_action_desc_only"));

                Path runOnlyDir = dynamicRoot.resolve("demo_action_run_only");
                Files.createDirectories(runOnlyDir);
                writeRunFile(runOnlyDir);
                waitForCondition(() -> existedMetaActions.containsKey("local::demo_action_run_only"), 500);
                Assertions.assertFalse(existedMetaActions.containsKey("local::demo_action_run_only"));
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        void testDynamicWatchAtomicDescOverwrite(@TempDir Path tempDir) throws IOException, InterruptedException {
            ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

            try {
                Path dynamicRoot = tempDir.resolve("action").resolve("dynamic");
                Path actionDir = dynamicRoot.resolve("demo_action_atomic");
                Files.createDirectories(actionDir);

                writeRunFile(actionDir);
                writeDescJson(actionDir, "before");
                waitForCondition(() -> existedMetaActions.containsKey("local::demo_action_atomic"), 2000);
                Assertions.assertTrue(existedMetaActions.containsKey("local::demo_action_atomic"));

                writeDescJsonAtomic(actionDir, "after");
                waitForCondition(() -> {
                    MetaActionInfo info = getMetaActionInfo(existedMetaActions, "local::demo_action_atomic");
                    return info != null && "after".equals(info.getDescription());
                }, 2000);

                MetaActionInfo info = getMetaActionInfo(existedMetaActions, "local::demo_action_atomic");
                Assertions.assertNotNull(info);
                Assertions.assertEquals("after", info.getDescription());
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        void testDynamicWatchRapidDescModify(@TempDir Path tempDir) throws IOException, InterruptedException {
            ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

            try {
                Path dynamicRoot = tempDir.resolve("action").resolve("dynamic");
                Path actionDir = dynamicRoot.resolve("demo_action_rapid");
                Files.createDirectories(actionDir);

                writeRunFile(actionDir);
                writeDescJson(actionDir, "v0");
                waitForCondition(() -> existedMetaActions.containsKey("local::demo_action_rapid"), 2000);
                Assertions.assertTrue(existedMetaActions.containsKey("local::demo_action_rapid"));

                String last = "v5";
                for (int i = 1; i <= 5; i++) {
                    writeDescJson(actionDir, "v" + i);
                }

                waitForCondition(() -> {
                    MetaActionInfo info = getMetaActionInfo(existedMetaActions, "local::demo_action_rapid");
                    return info != null && last.equals(info.getDescription());
                }, 2000);

                MetaActionInfo info = getMetaActionInfo(existedMetaActions, "local::demo_action_rapid");
                Assertions.assertNotNull(info);
                Assertions.assertEquals(last, info.getDescription());
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        void testDynamicWatchDeleteBehavior(@TempDir Path tempDir) throws IOException, InterruptedException {
            ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

            try {
                Path dynamicRoot = tempDir.resolve("action").resolve("dynamic");
                Path actionDir = dynamicRoot.resolve("demo_action_delete");
                Files.createDirectories(actionDir);
                Thread.sleep(100);
                writeRunFile(actionDir);
                writeDescJson(actionDir, "delete test");
                waitForCondition(() -> existedMetaActions.containsKey("local::demo_action_delete"), 2000);
                Assertions.assertTrue(existedMetaActions.containsKey("local::demo_action_delete"));

                Files.deleteIfExists(actionDir.resolve("run.py"));
                waitForCondition(() -> !existedMetaActions.containsKey("local::demo_action_delete"), 2000);
                Assertions.assertFalse(existedMetaActions.containsKey("local::demo_action_delete"));

                writeRunFile(actionDir);
                writeDescJson(actionDir, "delete test restore");
                waitForCondition(() -> existedMetaActions.containsKey("local::demo_action_delete"), 2000);
                Assertions.assertTrue(existedMetaActions.containsKey("local::demo_action_delete"));

                deleteDirectory(actionDir);
                waitForCondition(() -> !existedMetaActions.containsKey("local::demo_action_delete"), 2000);
                Assertions.assertFalse(existedMetaActions.containsKey("local::demo_action_delete"));
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        void testDynamicWatchInvalidDescRecovery(@TempDir Path tempDir) throws IOException, InterruptedException {
            ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());
            Thread.sleep(100);
            try {
                Path dynamicRoot = tempDir.resolve("action").resolve("dynamic");
                Path actionDir = dynamicRoot.resolve("demo_action_invalid");
                Files.createDirectories(actionDir);

                writeRunFile(actionDir);
                writeInvalidDescJson(actionDir);
                waitForCondition(() -> existedMetaActions.containsKey("local::demo_action_invalid"), 500);
                Assertions.assertFalse(existedMetaActions.containsKey("local::demo_action_invalid"));

                writeDescJson(actionDir, "fixed");
                waitForCondition(() -> existedMetaActions.containsKey("local::demo_action_invalid"), 2000);
                Assertions.assertTrue(existedMetaActions.containsKey("local::demo_action_invalid"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Nested
    class DescMcpTest {

        @Test
        void testDescMcpWatchCreateModifyDelete(@TempDir Path tempDir) throws IOException, InterruptedException {
            ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            String actionKey = "local::desc_action";
            existedMetaActions.put(actionKey, buildMetaActionInfo("base"));
            new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

            try {
                Path descDir = tempDir.resolve("action").resolve("mcp").resolve("desc");
                Files.createDirectories(descDir);

                writeDescMcpJson(descDir, actionKey, "v1");
                waitForCondition(() -> {
                    MetaActionInfo info = getMetaActionInfo(existedMetaActions, actionKey);
                    return info != null && "v1".equals(info.getDescription());
                }, 2000);
                MetaActionInfo info = getMetaActionInfo(existedMetaActions, actionKey);
                Assertions.assertNotNull(info);
                Assertions.assertEquals("v1", info.getDescription());
                Assertions.assertTrue(info.isIo());
                Assertions.assertTrue(info.isStrictDependencies());
                Assertions.assertFalse(info.getTags().isEmpty());

                writeDescMcpJson(descDir, actionKey, "v2");
                waitForCondition(() -> {
                    MetaActionInfo current = getMetaActionInfo(existedMetaActions, actionKey);
                    return current != null && "v2".equals(current.getDescription());
                }, 2000);
                info = getMetaActionInfo(existedMetaActions, actionKey);
                Assertions.assertNotNull(info);
                Assertions.assertEquals("v2", info.getDescription());

                Files.deleteIfExists(descDir.resolve(actionKey + ".desc.json"));
                waitForCondition(() -> {
                    MetaActionInfo current = getMetaActionInfo(existedMetaActions, actionKey);
                    return current != null
                            && !current.isIo()
                            && !current.isStrictDependencies()
                            && current.getTags().isEmpty()
                            && current.getPreActions().isEmpty()
                            && current.getPostActions().isEmpty();
                }, 2000);
                info = getMetaActionInfo(existedMetaActions, actionKey);
                Assertions.assertNotNull(info);
                Assertions.assertFalse(info.isIo());
                Assertions.assertFalse(info.isStrictDependencies());
                Assertions.assertTrue(info.getTags().isEmpty());
                Assertions.assertTrue(info.getPreActions().isEmpty());
                Assertions.assertTrue(info.getPostActions().isEmpty());
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        void testDescMcpInvalidJsonRecovery(@TempDir Path tempDir) throws IOException, InterruptedException {
            ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            String actionKey = "local::desc_invalid";
            existedMetaActions.put(actionKey, buildMetaActionInfo("base"));
            new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

            try {
                Path descDir = tempDir.resolve("action").resolve("mcp").resolve("desc");
                Files.createDirectories(descDir);

                writeInvalidDescMcpJson(descDir, actionKey);
                waitForCondition(() -> {
                    MetaActionInfo info = getMetaActionInfo(existedMetaActions, actionKey);
                    return info != null
                            && !info.isIo()
                            && !info.isStrictDependencies()
                            && info.getTags().isEmpty()
                            && info.getPreActions().isEmpty()
                            && info.getPostActions().isEmpty();
                }, 2000);
                MetaActionInfo info = getMetaActionInfo(existedMetaActions, actionKey);
                Assertions.assertNotNull(info);
                Assertions.assertFalse(info.isIo());
                Assertions.assertFalse(info.isStrictDependencies());
                Assertions.assertTrue(info.getTags().isEmpty());
                Assertions.assertTrue(info.getPreActions().isEmpty());
                Assertions.assertTrue(info.getPostActions().isEmpty());

                writeDescMcpJson(descDir, actionKey, "fixed");
                waitForCondition(() -> {
                    MetaActionInfo current = getMetaActionInfo(existedMetaActions, actionKey);
                    return current != null && "fixed".equals(current.getDescription());
                }, 2000);
                info = getMetaActionInfo(existedMetaActions, actionKey);
                Assertions.assertNotNull(info);
                Assertions.assertEquals("fixed", info.getDescription());
                Assertions.assertTrue(info.isIo());
                Assertions.assertTrue(info.isStrictDependencies());
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        void testDescMcpIgnoreInvalidFileName(@TempDir Path tempDir) throws IOException, InterruptedException {
            ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            String actionKey = "local::desc_ignore";
            existedMetaActions.put(actionKey, buildMetaActionInfo("base"));
            new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

            try {
                Path descDir = tempDir.resolve("action").resolve("mcp").resolve("desc");
                Files.createDirectories(descDir);

                Files.writeString(descDir.resolve("local-desc.desc.json"), "{ \"description\": \"bad\" }");
                Files.writeString(descDir.resolve(actionKey + ".json"), "{ \"description\": \"bad\" }");
                waitForCondition(() -> existedMetaActions.size() > 1, 500);

                MetaActionInfo info = getMetaActionInfo(existedMetaActions, actionKey);
                Assertions.assertNotNull(info);
                Assertions.assertEquals("base", info.getDescription());
                Assertions.assertTrue(info.isIo());
                Assertions.assertEquals(1, existedMetaActions.size());
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        void testDescMcpNoActionKeyPresent(@TempDir Path tempDir) throws IOException, InterruptedException {
            ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

            try {
                Path descDir = tempDir.resolve("action").resolve("mcp").resolve("desc");
                Files.createDirectories(descDir);

                String actionKey = "local::missing_action";
                writeDescMcpJson(descDir, actionKey, "desc");
                waitForCondition(() -> existedMetaActions.containsKey(actionKey), 500);
                Assertions.assertFalse(existedMetaActions.containsKey(actionKey));
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        void testDescMcpRapidCreateDelete(@TempDir Path tempDir) throws IOException, InterruptedException {
            ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            String actionKey = "local::desc_rapid";
            existedMetaActions.put(actionKey, buildMetaActionInfo("base"));
            new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

            try {
                Path descDir = tempDir.resolve("action").resolve("mcp").resolve("desc");
                Files.createDirectories(descDir);

                writeDescMcpJson(descDir, actionKey, "v1");
                Files.deleteIfExists(descDir.resolve(actionKey + ".desc.json"));

                waitForCondition(() -> {
                    MetaActionInfo info = getMetaActionInfo(existedMetaActions, actionKey);
                    return info != null
                            && !info.isIo()
                            && !info.isStrictDependencies()
                            && info.getTags().isEmpty()
                            && info.getPreActions().isEmpty()
                            && info.getPostActions().isEmpty();
                }, 2000);
                MetaActionInfo info = getMetaActionInfo(existedMetaActions, actionKey);
                Assertions.assertNotNull(info);
                Assertions.assertFalse(info.isIo());
                Assertions.assertFalse(info.isStrictDependencies());
                Assertions.assertTrue(info.getTags().isEmpty());
                Assertions.assertTrue(info.getPreActions().isEmpty());
                Assertions.assertTrue(info.getPostActions().isEmpty());
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        void testDescMcpRapidDeleteCreate(@TempDir Path tempDir) throws IOException, InterruptedException {
            ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            String actionKey = "local::desc_rapid_restore";
            existedMetaActions.put(actionKey, buildMetaActionInfo("base"));
            new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

            try {
                Path descDir = tempDir.resolve("action").resolve("mcp").resolve("desc");
                Files.createDirectories(descDir);

                writeDescMcpJson(descDir, actionKey, "v1");
                waitForCondition(() -> {
                    MetaActionInfo info = getMetaActionInfo(existedMetaActions, actionKey);
                    return info != null && "v1".equals(info.getDescription());
                }, 2000);

                Files.deleteIfExists(descDir.resolve(actionKey + ".desc.json"));
                waitForCondition(() -> {
                    MetaActionInfo info = getMetaActionInfo(existedMetaActions, actionKey);
                    return info != null
                            && !info.isIo()
                            && !info.isStrictDependencies()
                            && info.getTags().isEmpty()
                            && info.getPreActions().isEmpty()
                            && info.getPostActions().isEmpty();
                }, 2000);

                writeDescMcpJson(descDir, actionKey, "v2");
                waitForCondition(() -> {
                    MetaActionInfo info = getMetaActionInfo(existedMetaActions, actionKey);
                    return info != null && "v2".equals(info.getDescription());
                }, 2000);
                MetaActionInfo info = getMetaActionInfo(existedMetaActions, actionKey);
                Assertions.assertNotNull(info);
                Assertions.assertEquals("v2", info.getDescription());
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        void testDescMcpDirDeleteRecreate(@TempDir Path tempDir) throws IOException, InterruptedException {
            ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            String actionKey = "local::desc_dir_restore";
            existedMetaActions.put(actionKey, buildMetaActionInfo("base"));
            new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

            try {
                Path descDir = tempDir.resolve("action").resolve("mcp").resolve("desc");
                Files.createDirectories(descDir);

                writeDescMcpJson(descDir, actionKey, "v1");
                waitForCondition(() -> {
                    MetaActionInfo info = getMetaActionInfo(existedMetaActions, actionKey);
                    return info != null && "v1".equals(info.getDescription());
                }, 2000);

                Files.deleteIfExists(descDir.resolve(actionKey + ".desc.json"));
                deleteDirectory(descDir);
                waitForCondition(() -> {
                    MetaActionInfo info = getMetaActionInfo(existedMetaActions, actionKey);
                    return info != null
                            && !info.isIo()
                            && !info.isStrictDependencies()
                            && info.getTags().isEmpty()
                            && info.getPreActions().isEmpty()
                            && info.getPostActions().isEmpty();
                }, 2000);

                Files.createDirectories(descDir);
                writeDescMcpJson(descDir, actionKey, "v2");
                waitForCondition(() -> {
                    MetaActionInfo info = getMetaActionInfo(existedMetaActions, actionKey);
                    return info != null && "v2".equals(info.getDescription());
                }, 2000);
                MetaActionInfo info = getMetaActionInfo(existedMetaActions, actionKey);
                Assertions.assertNotNull(info);
                Assertions.assertEquals("v2", info.getDescription());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Nested
    class CommonMcpTest {

        @Test
        void testCommonMcpInitialLoad(@TempDir Path tempDir) throws IOException, InterruptedException {
            ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            Path mcpDir = tempDir.resolve("action").resolve("mcp");
            Files.createDirectories(mcpDir);
            Path configFile = mcpDir.resolve("servers.json");
            String config = buildCommonMcpConfig(
                    buildStdioServerEntry("mcp-deepwiki", "mcp-deepwiki@latest")
            );
            writeCommonMcpConfig(configFile, config);

            new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

            try {
                waitForCondition(() -> hasActionKey(existedMetaActions, key -> key.startsWith("mcp-deepwiki::")), 20000);
                Assertions.assertTrue(hasActionKey(existedMetaActions, key -> key.startsWith("mcp-deepwiki::")));
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        void testCommonMcpCreateModifyDelete(@TempDir Path tempDir) throws IOException, InterruptedException {
            ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

            try {
                Path mcpDir = tempDir.resolve("action").resolve("mcp");
                Files.createDirectories(mcpDir);
                Path configFile = mcpDir.resolve("servers.json");

                String config = buildCommonMcpConfig(
                        buildStdioServerEntry("mcp-deepwiki", "mcp-deepwiki@latest")
                );
                writeCommonMcpConfig(configFile, config);
                waitForCondition(() -> hasActionKey(existedMetaActions, key -> key.startsWith("mcp-deepwiki::")), 20000);
                Assertions.assertTrue(hasActionKey(existedMetaActions, key -> key.startsWith("mcp-deepwiki::")));

                String updatedConfig = buildCommonMcpConfig(
                        buildStdioServerEntry("mcp-deepwiki", "mcp-deepwiki@latest"),
                        buildStdioServerEntry("playwright", "@playwright/mcp@latest")
                );
                writeCommonMcpConfig(configFile, updatedConfig);
                waitForCondition(() -> hasActionKey(existedMetaActions, key -> key.startsWith("playwright::")), 20000);
                Assertions.assertTrue(hasActionKey(existedMetaActions, key -> key.startsWith("playwright::")));

                Files.deleteIfExists(configFile);
                waitForCondition(() -> !hasActionKey(existedMetaActions, key -> key.startsWith("mcp-deepwiki::")), 20000);
                Assertions.assertFalse(hasActionKey(existedMetaActions, key -> key.startsWith("mcp-deepwiki::")));
                Assertions.assertFalse(hasActionKey(existedMetaActions, key -> key.startsWith("playwright::")));
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        void testCommonMcpInvalidJsonRecovery(@TempDir Path tempDir) throws IOException, InterruptedException {
            ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

            try {
                Path mcpDir = tempDir.resolve("action").resolve("mcp");
                Files.createDirectories(mcpDir);
                Path configFile = mcpDir.resolve("servers.json");

                writeCommonMcpConfig(configFile, "{ invalid json");
                waitForCondition(() -> hasActionKey(existedMetaActions, key -> key.startsWith("mcp-deepwiki::")), 2000);
                Assertions.assertFalse(hasActionKey(existedMetaActions, key -> key.startsWith("mcp-deepwiki::")));

                String config = buildCommonMcpConfig(
                        buildStdioServerEntry("mcp-deepwiki", "mcp-deepwiki@latest")
                );
                writeCommonMcpConfig(configFile, config);
                waitForCondition(() -> hasActionKey(existedMetaActions, key -> key.startsWith("mcp-deepwiki::")), 20000);
                Assertions.assertTrue(hasActionKey(existedMetaActions, key -> key.startsWith("mcp-deepwiki::")));
            } finally {
                executor.shutdownNow();
            }
        }
    }

}
