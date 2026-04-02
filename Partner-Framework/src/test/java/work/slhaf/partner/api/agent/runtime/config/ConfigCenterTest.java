package work.slhaf.partner.api.agent.runtime.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

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

    private static final Path INITIAL_PATH = Path.of("root-initial.json");
    private static final Path NESTED_PATH = Path.of("nested", "child.json");
    private static final Path DELETE_PATH = Path.of("delete", "target.json");
    private static final Path INVALID_PATH = Path.of("invalid", "target.json");
    private static final Path IDEMPOTENT_PATH = Path.of("idempotent", "target.json");

    private static String originalUserHome;
    private static Path configDir;
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

        configDir = ConfigCenter.INSTANCE.getPaths().getConfigDir();
        Files.createDirectories(configDir);
        Files.createDirectories(configDir.resolve(NESTED_PATH).getParent());
        Files.createDirectories(configDir.resolve(DELETE_PATH).getParent());
        Files.createDirectories(configDir.resolve(INVALID_PATH).getParent());
        Files.createDirectories(configDir.resolve(IDEMPOTENT_PATH).getParent());
        writeJson(configDir.resolve(INITIAL_PATH), "initial", 1);

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
        if (originalUserHome == null) {
            System.clearProperty("user.home");
        } else {
            System.setProperty("user.home", originalUserHome);
        }
    }

    private static int totalReloadCount() {
        return initialRegistration.reloadCount()
                + nestedRegistration.reloadCount()
                + deleteRegistration.reloadCount()
                + invalidRegistration.reloadCount()
                + idempotentRegistration.reloadCount();
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
    void testInitialReconcileReloadsRegisteredJson() throws Exception {
        waitForCount(initialRegistration, 1, 3000);

        Assertions.assertEquals(1, initialRegistration.reloadCount());
        Assertions.assertEquals("initial", initialRegistration.lastConfig().name);
        Assertions.assertEquals(1, initialRegistration.lastConfig().version);
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

    public static class TestConfig extends Config {
        public String name;
        public int version;
    }

    private static class TrackingRegistration implements ConfigRegistration<TestConfig> {
        private final AtomicInteger reloadCount = new AtomicInteger();
        private final AtomicReference<TestConfig> lastConfig = new AtomicReference<>();

        @Override
        public Class<TestConfig> type() {
            return TestConfig.class;
        }

        @Override
        public void init(TestConfig config) {
        }

        @Override
        public void onReload(TestConfig config) {
            lastConfig.set(config);
            reloadCount.incrementAndGet();
        }

        int reloadCount() {
            return reloadCount.get();
        }

        TestConfig lastConfig() {
            return lastConfig.get();
        }
    }
}
