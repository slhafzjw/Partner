package work.slhaf.partner.framework.agent.runtime.config;

import com.alibaba.fastjson2.JSONObject;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import work.slhaf.partner.framework.agent.config.Config;
import work.slhaf.partner.framework.agent.config.ConfigCenter;
import work.slhaf.partner.framework.agent.config.ConfigDoc;
import work.slhaf.partner.framework.agent.config.ConfigRegistration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConfigCenterTest {

    private static final Path TEST_ROOT = Path.of("target", "config-center-test");
    private static final Path INITIAL_PATH = TEST_ROOT.resolve("root-initial.json");
    private static final Path NESTED_PATH = TEST_ROOT.resolve(Path.of("nested", "child.json"));
    private static final Path DELETE_PATH = TEST_ROOT.resolve(Path.of("delete", "target.json"));
    private static final Path INVALID_PATH = TEST_ROOT.resolve(Path.of("invalid", "target.json"));
    private static final Path IDEMPOTENT_PATH = TEST_ROOT.resolve(Path.of("idempotent", "target.json"));

    private static String originalUserHome;
    private static Path configDir;
    private static Path workingDir;
    private static TrackingRegistration initialRegistration;
    private static TrackingRegistration nestedRegistration;
    private static TrackingRegistration deleteRegistration;
    private static TrackingRegistration invalidRegistration;
    private static TrackingRegistration idempotentRegistration;

    @BeforeAll
    static void beforeAll(@TempDir Path tempUserHome) throws Exception {
        Assumptions.assumeTrue(System.getenv("PARTNER_HOME") == null,
                "PARTNER_HOME is set; user.home based ConfigCenter test is not applicable.");

        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempUserHome.toString());

        initialRegistration = new TrackingRegistration();
        nestedRegistration = new TrackingRegistration();
        deleteRegistration = new TrackingRegistration();
        invalidRegistration = new TrackingRegistration();
        idempotentRegistration = new TrackingRegistration();

        workingDir = Path.of("").toAbsolutePath().normalize();
        configDir = ConfigCenter.INSTANCE.getPaths().getConfigDir();
        Files.createDirectories(configDir);
        Files.createDirectories(configDir.resolve(NESTED_PATH).getParent());
        Files.createDirectories(configDir.resolve(DELETE_PATH).getParent());
        Files.createDirectories(configDir.resolve(INVALID_PATH).getParent());
        Files.createDirectories(configDir.resolve(IDEMPOTENT_PATH).getParent());
        writeJson(workingDir.resolve(INITIAL_PATH), "initial-init", 1);
        writeJson(workingDir.resolve(NESTED_PATH), "nested-init", 1);
        writeJson(workingDir.resolve(DELETE_PATH), "delete-init", 1);
        writeJson(workingDir.resolve(INVALID_PATH), "invalid-init", 1);
        writeJson(workingDir.resolve(IDEMPOTENT_PATH), "idempotent-init", 1);
        writeJson(configDir.resolve(INITIAL_PATH), "initial-config-dir", 1);

        ConfigCenter.INSTANCE.register(() -> {
            Map<Path, ConfigRegistration<? extends Config>> declared = new LinkedHashMap<>();
            declared.put(INITIAL_PATH, initialRegistration);
            declared.put(NESTED_PATH, nestedRegistration);
            declared.put(DELETE_PATH, deleteRegistration);
            declared.put(INVALID_PATH, invalidRegistration);
            declared.put(IDEMPOTENT_PATH, idempotentRegistration);
            return declared;
        });
        ConfigCenter.INSTANCE.start();
    }

    @AfterAll
    static void afterAll() {
        ConfigCenter.INSTANCE.close();
        deleteRecursivelyIfExists(workingDir.resolve(TEST_ROOT));
        if (originalUserHome == null) {
            System.clearProperty("user.home");
        } else {
            System.setProperty("user.home", originalUserHome);
        }
    }

    private static int totalInitCount() {
        return initialRegistration.initCount()
                + nestedRegistration.initCount()
                + deleteRegistration.initCount()
                + invalidRegistration.initCount()
                + idempotentRegistration.initCount();
    }

    private static int totalReloadCount() {
        return initialRegistration.reloadCount()
                + nestedRegistration.reloadCount()
                + deleteRegistration.reloadCount()
                + invalidRegistration.reloadCount()
                + idempotentRegistration.reloadCount();
    }

    private static void deleteRecursivelyIfExists(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static void writeJson(Path file, String name, int version) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file,
                "{\"name\":\"" + name + "\",\"version\":" + version + "}",
                StandardCharsets.UTF_8);
    }

    private static void waitForCount(TrackingRegistration registration, int expectedCount, long timeoutMs)
            throws InterruptedException {
        waitForCondition(() -> registration.reloadCount() >= expectedCount, timeoutMs);
    }

    private static void waitForConfig(TrackingRegistration registration, String expectedName, int expectedVersion,
                                      long timeoutMs) throws InterruptedException {
        waitForCondition(() -> hasConfig(registration, expectedName, expectedVersion), timeoutMs);
    }

    private static boolean hasConfig(TrackingRegistration registration, String expectedName, int expectedVersion) {
        TestConfig config = registration.lastConfig();
        return config != null
                && expectedName.equals(config.name)
                && expectedVersion == config.version;
    }

    private static void waitForCondition(BooleanSupplier supplier, long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (!supplier.getAsBoolean()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                break;
            }
            Thread.sleep(50);
        }
        Assertions.assertTrue(supplier.getAsBoolean(), "Condition was not satisfied within " + timeoutMs + " ms");
    }

    @Test
    @Order(1)
    void testStartOnlyInitializesOneRegisteredConfigAndDoesNotTriggerReload() {
        Assertions.assertEquals(1, totalInitCount());
        Assertions.assertEquals(0, totalReloadCount());
    }

    @Test
    @Order(2)
    void testNestedJsonCreateAndModifyTriggersReload() throws Exception {
        Path file = configDir.resolve(NESTED_PATH);

        writeJson(file, "nested-create", 1);
        waitForConfig(nestedRegistration, "nested-create", 1, 3000);
        Assertions.assertEquals("nested-create", nestedRegistration.lastConfig().name);
        Assertions.assertEquals(1, nestedRegistration.lastConfig().version);

        int baseline = nestedRegistration.reloadCount();
        writeJson(file, "nested-modify", 2);
        waitForCondition(() -> nestedRegistration.reloadCount() > baseline
                && hasConfig(nestedRegistration, "nested-modify", 2), 3000);
        Assertions.assertEquals("nested-modify", nestedRegistration.lastConfig().name);
        Assertions.assertEquals(2, nestedRegistration.lastConfig().version);
    }

    @Test
    @Order(3)
    void testUnregisteredJsonDoesNotTriggerReload() throws Exception {
        int totalBaseline = totalReloadCount();

        writeJson(configDir.resolve("unregistered.json"), "ignored", 1);
        Thread.sleep(300);

        Assertions.assertEquals(totalBaseline, totalReloadCount());
    }

    @Test
    @Order(4)
    void testNonJsonDoesNotTriggerReload() throws Exception {
        int totalBaseline = totalReloadCount();

        Path file = configDir.resolve("nested").resolve("ignored.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "ignored", StandardCharsets.UTF_8);
        Thread.sleep(300);

        Assertions.assertEquals(totalBaseline, totalReloadCount());
    }

    @Test
    @Order(5)
    void testDeleteDoesNotTriggerReload() throws Exception {
        Path file = configDir.resolve(DELETE_PATH);
        writeJson(file, "delete-target", 1);
        waitForCount(deleteRegistration, 1, 3000);

        int baseline = deleteRegistration.reloadCount();
        Files.delete(file);
        Thread.sleep(300);

        Assertions.assertEquals(baseline, deleteRegistration.reloadCount());
    }

    @Test
    @Order(6)
    void testInvalidJsonDoesNotReloadButRecoveryStillWorks() throws Exception {
        Path file = configDir.resolve(INVALID_PATH);
        writeJson(file, "valid-before-invalid", 1);
        waitForCount(invalidRegistration, 1, 3000);

        int baseline = invalidRegistration.reloadCount();
        Files.writeString(file, "{\"name\":", StandardCharsets.UTF_8);
        Thread.sleep(300);
        Assertions.assertEquals(baseline, invalidRegistration.reloadCount());

        writeJson(file, "valid-after-invalid", 2);
        waitForCount(invalidRegistration, baseline + 1, 3000);
        Assertions.assertEquals("valid-after-invalid", invalidRegistration.lastConfig().name);
        Assertions.assertEquals(2, invalidRegistration.lastConfig().version);
    }

    @Test
    @Order(7)
    void testStartIsIdempotent() throws Exception {
        Path file = configDir.resolve(IDEMPOTENT_PATH);
        writeJson(file, "before-idempotent", 1);
        waitForCount(idempotentRegistration, 1, 3000);

        ConfigCenter.INSTANCE.start();

        int baseline = idempotentRegistration.reloadCount();
        writeJson(file, "after-idempotent", 2);
        waitForCount(idempotentRegistration, baseline + 1, 3000);
        Thread.sleep(300);

        Assertions.assertEquals(baseline + 1, idempotentRegistration.reloadCount());
        Assertions.assertEquals("after-idempotent", idempotentRegistration.lastConfig().name);
        Assertions.assertEquals(2, idempotentRegistration.lastConfig().version);
    }

    private static String resolveConfigDoc(Class<? extends Config> type) throws Exception {
        var method = java.util.Arrays.stream(ConfigCenter.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().startsWith("resolveConfigDoc"))
                .filter(candidate -> candidate.getParameterCount() == 1)
                .findFirst()
                .orElseThrow();
        method.setAccessible(true);
        return (String) method.invoke(ConfigCenter.INSTANCE, type);
    }

    @Test
    @Order(8)
    void testResolveConfigDocForJavaConfig() throws Exception {
        String doc = resolveConfigDoc(JavaDocConfig.class);

        Assertions.assertEquals("""
                Expected fields:
                - port: Int
                  Description: WebSocket 监听端口
                  Example: 29600
                  Nullable: false (inferred from missing nullability annotation, may be unreliable)
                
                - heartbeatInterval: Int
                  Description: 心跳间隔
                  Unit: ms
                  Constraint: > 0
                  Example: 10000
                  Nullable: false (inferred from missing nullability annotation, may be unreliable)
                
                - tag: String
                  Description: 标签
                  Nullable: true
                """.stripTrailing(), doc);
    }

    @Test
    @Order(9)
    void testResolveConfigDocForKotlinConfig() throws Exception {
        String doc = resolveConfigDoc(KotlinDocConfig.class);

        Assertions.assertEquals("""
                Expected fields:
                - port: Int
                  Description: WebSocket 监听端口
                  Example: 29600
                  Nullable: false
                
                - heartbeatInterval: Int
                  Description: 心跳间隔
                  Unit: ms
                  Constraint: > 0
                  Example: 10000
                  Nullable: true
                
                - tag: String
                  Description: 标签
                  Nullable: true
                """.stripTrailing(), doc);
    }

    public static class TestConfig extends Config {
        public String name;
        public int version;
    }

    public static class JavaDocConfig extends Config {
        @ConfigDoc(description = "WebSocket 监听端口", example = "29600")
        public int port;

        @ConfigDoc(description = "心跳间隔", unit = "ms", constraint = "> 0", example = "10000")
        public int heartbeatInterval;

        @Nullable
        @ConfigDoc(description = "标签")
        public String tag;
    }

    private static class TrackingRegistration implements ConfigRegistration<TestConfig> {
        private final AtomicInteger initCount = new AtomicInteger();
        private final AtomicInteger reloadCount = new AtomicInteger();
        private final AtomicReference<TestConfig> lastConfig = new AtomicReference<>();

        @Override
        public Class<TestConfig> type() {
            return TestConfig.class;
        }

        @Override
        public void init(TestConfig config, JSONObject json) {
            initCount.incrementAndGet();
        }

        @Override
        public void onReload(TestConfig config, JSONObject json) {
            lastConfig.set(config);
            reloadCount.incrementAndGet();
        }

        int initCount() {
            return initCount.get();
        }

        int reloadCount() {
            return reloadCount.get();
        }

        TestConfig lastConfig() {
            return lastConfig.get();
        }

        @Override
        public @Nullable TestConfig defaultConfig() {
            return null;
        }
    }
}
