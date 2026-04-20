package work.slhaf.partner.module.action.builtin;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import work.slhaf.partner.core.action.runner.policy.ExecutionPolicy;
import work.slhaf.partner.core.action.runner.policy.ExecutionPolicyRegistry;

import java.lang.reflect.Field;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class BuiltinCommandActionProviderTtlTest {

    private static String originalUserHome;

    @BeforeAll
    static void prepareTestHome() throws IOException {
        originalUserHome = System.getProperty("user.home");
        Path tempHome = Files.createTempDirectory("partner-test-home");
        System.setProperty("user.home", tempHome.toString());
        ExecutionPolicyRegistry.INSTANCE.updatePolicy(new ExecutionPolicy(
                ExecutionPolicy.Mode.DIRECT,
                "direct",
                ExecutionPolicy.Network.ENABLE,
                true,
                Map.of(),
                null,
                Set.of(),
                Set.of()
        ));
    }

    @AfterAll
    static void restoreUserHome() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void testOverviewRemovesExpiredFinishedSessions() throws Exception {
        BuiltinCommandActionProvider provider = new BuiltinCommandActionProvider();
        List<BuiltinActionRegistry.BuiltinActionDefinition> definitions = provider.provideBuiltinActions();
        BuiltinActionRegistry.BuiltinActionDefinition start = requireDefinition(definitions, "builtin::command::start");
        BuiltinActionRegistry.BuiltinActionDefinition overview = requireDefinition(definitions, "builtin::command::overview");
        BuiltinActionRegistry.BuiltinActionDefinition inspect = requireDefinition(definitions, "builtin::command::inspect");

        String startResult = start.invoker().apply(Map.of(
                "desc", "ttl-session",
                "arg", "sh",
                "arg1", "-lc",
                "arg2", "printf 'done'"
        ));
        String executionId = JSONObject.parseObject(startResult).getString("executionId");
        waitForInspectExit(inspect, executionId);

        expireHandle(provider, executionId);

        JSONObject overviewResult = JSONObject.parseObject(overview.invoker().apply(Map.of()));
        JSONArray result = overviewResult.getJSONArray("result");
        Assertions.assertTrue(result.stream().map(item -> (JSONObject) item)
                .noneMatch(item -> executionId.equals(item.getString("executionId"))));
    }

    @Test
    void testInspectRejectsExpiredFinishedSession() throws Exception {
        BuiltinCommandActionProvider provider = new BuiltinCommandActionProvider();
        List<BuiltinActionRegistry.BuiltinActionDefinition> definitions = provider.provideBuiltinActions();
        BuiltinActionRegistry.BuiltinActionDefinition start = requireDefinition(definitions, "builtin::command::start");
        BuiltinActionRegistry.BuiltinActionDefinition inspect = requireDefinition(definitions, "builtin::command::inspect");

        String startResult = start.invoker().apply(Map.of(
                "desc", "ttl-session-inspect",
                "arg", "sh",
                "arg1", "-lc",
                "arg2", "printf 'done'"
        ));
        String executionId = JSONObject.parseObject(startResult).getString("executionId");
        waitForInspectExit(inspect, executionId);

        expireHandle(provider, executionId);

        Assertions.assertThrows(IllegalArgumentException.class, () -> inspect.invoker().apply(Map.of(
                "id", executionId
        )));
    }

    private void expireHandle(BuiltinCommandActionProvider provider, String executionId) throws Exception {
        Field handlesField = BuiltinCommandActionProvider.class.getDeclaredField("commandHandles");
        handlesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Object> handles = (ConcurrentHashMap<String, Object>) handlesField.get(provider);
        Object handle = handles.get(executionId);
        Assertions.assertNotNull(handle);

        Field exitCodeField = handle.getClass().getDeclaredField("exitCode");
        exitCodeField.setAccessible(true);
        exitCodeField.set(handle, 0);

        Field exitAtField = handle.getClass().getDeclaredField("exitAt");
        exitAtField.setAccessible(true);
        exitAtField.set(handle, Instant.now().minus(11, ChronoUnit.MINUTES));
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
