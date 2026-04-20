package work.slhaf.partner.module.action.builtin;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import work.slhaf.partner.core.action.runner.policy.ExecutionPolicy;
import work.slhaf.partner.core.action.runner.policy.ExecutionPolicyRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

class BuiltinCommandActionProviderPolicyTest {

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
    void testExecuteAppliesExecutionPolicyEnvironment() {
        BuiltinCommandActionProvider provider = new BuiltinCommandActionProvider();
        BuiltinActionRegistry.BuiltinActionDefinition execute = requireDefinition(
                provider.provideBuiltinActions(),
                "builtin::command::execute"
        );

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
                Map.of("PARTNER_BUILTIN_TEST", "builtin-applied"),
                null,
                Set.of(),
                Set.of()
        ));

        try {
            String result = execute.invoker().apply(Map.of(
                    "arg", "sh",
                    "arg1", "-lc",
                    "arg2", "printf '%s' \"$PARTNER_BUILTIN_TEST\""
            ));
            Assertions.assertEquals("builtin-applied", JSONObject.parseObject(result).getString("result"));
        } finally {
            ExecutionPolicyRegistry.INSTANCE.updatePolicy(originalPolicy);
        }
    }

    @Test
    void testStartAppliesExecutionPolicyEnvironment() throws Exception {
        BuiltinCommandActionProvider provider = new BuiltinCommandActionProvider();
        List<BuiltinActionRegistry.BuiltinActionDefinition> definitions = provider.provideBuiltinActions();
        BuiltinActionRegistry.BuiltinActionDefinition start = requireDefinition(definitions, "builtin::command::start");
        BuiltinActionRegistry.BuiltinActionDefinition inspect = requireDefinition(definitions, "builtin::command::inspect");

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
                Map.of("PARTNER_BUILTIN_TEST", "builtin-session"),
                null,
                Set.of(),
                Set.of()
        ));

        try {
            String startResult = start.invoker().apply(Map.of(
                    "desc", "policy-session",
                    "arg", "sh",
                    "arg1", "-lc",
                    "arg2", "printf '%s' \"$PARTNER_BUILTIN_TEST\""
            ));
            String executionId = JSONObject.parseObject(startResult).getString("executionId");
            Assertions.assertNotNull(executionId);

            JSONObject inspectResult = waitForInspectExit(inspect, executionId);
            Assertions.assertTrue(inspectResult.getString("stdoutSummary").contains("builtin-session"));
        } finally {
            ExecutionPolicyRegistry.INSTANCE.updatePolicy(originalPolicy);
        }
    }

    private BuiltinActionRegistry.BuiltinActionDefinition requireDefinition(
            List<BuiltinActionRegistry.BuiltinActionDefinition> definitions,
            String actionKey
    ) {
        return definitions.stream()
                .filter(definition -> actionKey.equals(definition.actionKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("definition not found: " + actionKey));
    }

    private JSONObject waitForInspectExit(BuiltinActionRegistry.BuiltinActionDefinition inspectDefinition, String executionId) throws Exception {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            JSONObject inspect = JSONObject.parseObject(inspectDefinition.invoker().apply(Map.of(
                    "id", executionId
            )));
            if (inspect.get("exitCode") != null) {
                return inspect;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("command session did not exit in time");
    }
}
