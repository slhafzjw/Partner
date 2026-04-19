package work.slhaf.partner.core.action.runner.execution;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.runner.policy.ExecutionPolicy;
import work.slhaf.partner.core.action.runner.policy.ExecutionPolicyRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

class OriginExecutionServiceTest {

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
    void testOriginExecutionServiceAppliesExecutionPolicyEnvironment(@TempDir Path tempDir) throws IOException {
        Path script = tempDir.resolve("print_env.py");
        Files.writeString(script, "import os\nprint(os.getenv('PARTNER_ORIGIN_TEST', ''), end='')\n");

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
                Map.of("PARTNER_ORIGIN_TEST", "origin-applied"),
                null,
                Set.of(),
                Set.of()
        ));

        try {
            var prepared = ExecutionPolicyRegistry.INSTANCE.prepare(List.of("python3", script.toString()));
            Assertions.assertEquals("origin-applied", prepared.getEnvironment().get("PARTNER_ORIGIN_TEST"));
            var directExec = CommandExecutionService.INSTANCE.exec(prepared);
            Assertions.assertTrue(directExec.isOk());
            Assertions.assertEquals("origin-applied", directExec.getTotal());
            OriginExecutionService service = new OriginExecutionService();
            MetaAction metaAction = new MetaAction("run", false, "python3", MetaAction.Type.ORIGIN, script.toString());
            var response = service.run(metaAction);
            Assertions.assertTrue(response.isOk());
            Assertions.assertEquals("origin-applied", response.getData());
        } finally {
            ExecutionPolicyRegistry.INSTANCE.updatePolicy(originalPolicy);
        }
    }
}
