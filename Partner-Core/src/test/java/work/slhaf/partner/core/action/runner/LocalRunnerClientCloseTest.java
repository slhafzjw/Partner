package work.slhaf.partner.core.action.runner;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.runner.policy.ExecutionPolicyRegistry;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class LocalRunnerClientCloseTest {

    private static String originalUserHome;

    @BeforeAll
    static void prepareTestHome() throws IOException {
        originalUserHome = System.getProperty("user.home");
        Path tempHome = Files.createTempDirectory("partner-test-home");
        System.setProperty("user.home", tempHome.toString());
    }

    @AfterAll
    static void restoreUserHome() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void testLocalRunnerClientCloseUnregistersPolicyListener(@TempDir Path tempDir) throws Exception {
        Field listenersField = ExecutionPolicyRegistry.class.getDeclaredField("listeners");
        listenersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        CopyOnWriteArraySet<Object> listeners = (CopyOnWriteArraySet<Object>) listenersField.get(ExecutionPolicyRegistry.INSTANCE);
        int before = listeners.size();

        ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            LocalRunnerClient client = new LocalRunnerClient(existedMetaActions, executor, tempDir.toString());
            Assertions.assertEquals(before + 1, listeners.size());
            client.close();
            Assertions.assertEquals(before, listeners.size());
        }
    }
}
