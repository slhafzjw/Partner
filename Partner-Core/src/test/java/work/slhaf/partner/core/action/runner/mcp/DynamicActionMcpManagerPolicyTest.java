package work.slhaf.partner.core.action.runner.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.runner.policy.ExecutionPolicy;
import work.slhaf.partner.core.action.runner.policy.ExecutionPolicyRegistry;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;

class DynamicActionMcpManagerPolicyTest {

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
    @SuppressWarnings("unchecked")
    void testDynamicActionHandlerAppliesExecutionPolicyEnvironment(@TempDir Path tempDir) throws Exception {
        ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             DynamicActionMcpManager manager = new DynamicActionMcpManager(tempDir, existedMetaActions, executor)) {
            Path script = tempDir.resolve("run.py");
            Files.writeString(script, "import os\nprint(os.getenv('PARTNER_DYNAMIC_TEST', ''), end='')\n");

            ExecutionPolicy originalPolicy = new ExecutionPolicy(
                    ExecutionPolicy.Mode.DIRECT,
                    "direct",
                    ExecutionPolicy.Network.ENABLE,
                    true,
                    Map.of(),
                    null,
                    Set.of(),
                    Set.of()
            );
            ExecutionPolicyRegistry.INSTANCE.updatePolicy(new ExecutionPolicy(
                    ExecutionPolicy.Mode.DIRECT,
                    "direct",
                    ExecutionPolicy.Network.ENABLE,
                    false,
                    Map.of("PARTNER_DYNAMIC_TEST", "dynamic-applied"),
                    null,
                    Set.of(),
                    Set.of()
            ));

            try {
                Method method = DynamicActionMcpManager.class.getDeclaredMethod("buildToolHandler", File.class, String.class);
                method.setAccessible(true);
                BiFunction<?, McpSchema.CallToolRequest, Mono<McpSchema.CallToolResult>> handler =
                        (BiFunction<?, McpSchema.CallToolRequest, Mono<McpSchema.CallToolResult>>) method.invoke(
                                manager,
                                script.toFile(),
                                "python3"
                        );

                McpSchema.CallToolResult result = handler.apply(
                        null,
                        McpSchema.CallToolRequest.builder().name("demo").arguments(Map.of()).build()
                ).block();

                Assertions.assertNotNull(result);
                Assertions.assertFalse(Boolean.TRUE.equals(result.isError()));
                Assertions.assertEquals("[dynamic-applied]", String.valueOf(result.structuredContent()));
            } finally {
                ExecutionPolicyRegistry.INSTANCE.updatePolicy(originalPolicy);
            }
        }
    }
}
