package work.slhaf.partner.core.action.runner;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static work.slhaf.partner.core.action.runner.LocalRunnerClientTest.Await.waitForCondition;
import static work.slhaf.partner.core.action.runner.LocalRunnerClientTest.Common.*;
import static work.slhaf.partner.core.action.runner.LocalRunnerClientTest.Fs.*;

class LocalRunnerClientWatchDepthTest {

    @Test
    void testDynamicWatchIgnoresGrandchildDirectories(@TempDir Path tempDir) throws IOException, InterruptedException {
        ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

        try {
            Path nestedActionDir = tempDir.resolve("action").resolve("dynamic").resolve("group").resolve("demo_action_nested");
            Files.createDirectories(nestedActionDir);

            writeRunFile(nestedActionDir);
            writeDescJson(nestedActionDir, "nested action");
            waitForCondition(() -> existedMetaActions.containsKey("local::demo_action_nested"), 1000);

            Assertions.assertFalse(existedMetaActions.containsKey("local::demo_action_nested"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testDescMcpIgnoresNestedDirectories(@TempDir Path tempDir) throws IOException, InterruptedException {
        ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        String actionKey = "local::desc_nested";
        existedMetaActions.put(actionKey, buildMetaActionInfo("base"));
        new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

        try {
            Path nestedDescDir = tempDir.resolve("action").resolve("mcp").resolve("desc").resolve("nested");
            Files.createDirectories(nestedDescDir);

            writeDescMcpJson(nestedDescDir, actionKey, "nested override");
            waitForCondition(() -> {
                MetaActionInfo current = getMetaActionInfo(existedMetaActions, actionKey);
                return current != null && "nested override".equals(current.getDescription());
            }, 1000);

            MetaActionInfo info = getMetaActionInfo(existedMetaActions, actionKey);
            Assertions.assertNotNull(info);
            Assertions.assertEquals("base", info.getDescription());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testCommonMcpIgnoresNestedDirectories(@TempDir Path tempDir) throws IOException, InterruptedException {
        ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());

        try {
            Path nestedDir = tempDir.resolve("action").resolve("mcp").resolve("nested");
            Files.createDirectories(nestedDir);
            Path configFile = nestedDir.resolve("servers.json");

            String config = buildCommonMcpConfig(
                    buildStdioServerEntry("mcp-deepwiki", "mcp-deepwiki@latest")
            );
            writeCommonMcpConfig(configFile, config);
            waitForCondition(() -> hasActionKey(existedMetaActions, key -> key.startsWith("mcp-deepwiki::")), 2000);

            Assertions.assertFalse(hasActionKey(existedMetaActions, key -> key.startsWith("mcp-deepwiki::")));
        } finally {
            executor.shutdownNow();
        }
    }
}
