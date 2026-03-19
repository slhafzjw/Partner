package work.slhaf.partner.module.modules.action.builtin;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class BuiltinCommandActionProviderTest {

    @Test
    void testStartInspectReadAndOverview() throws Exception {
        BuiltinCommandActionProvider provider = new BuiltinCommandActionProvider();
        List<BuiltinActionRegistry.BuiltinActionDefinition> definitions = provider.provideBuiltinActions();
        BuiltinActionRegistry.BuiltinActionDefinition start = requireDefinition(definitions, "builtin::command::start");
        BuiltinActionRegistry.BuiltinActionDefinition inspectDefinition = requireDefinition(definitions, "builtin::command::inspect");
        BuiltinActionRegistry.BuiltinActionDefinition readDefinition = requireDefinition(definitions, "builtin::command::read");
        BuiltinActionRegistry.BuiltinActionDefinition overviewDefinition = requireDefinition(definitions, "builtin::command::overview");

        String startResult = start.invoker().apply(Map.of(
                "desc", "demo-session",
                "arg", "sh",
                "arg1", "-lc",
                "arg2", "printf 'hello\\nworld\\n'; printf 'oops\\n' >&2"
        ));
        String executionId = JSONObject.parseObject(startResult).getString("executionId");
        Assertions.assertNotNull(executionId);

        JSONObject inspect = waitForInspectExit(inspectDefinition, executionId);
        Assertions.assertEquals("demo-session", inspect.getString("desc"));
        Assertions.assertEquals(0, inspect.getInteger("exitCode"));
        Assertions.assertTrue(inspect.getInteger("stdoutSize") > 0);
        Assertions.assertTrue(inspect.getInteger("stderrSize") > 0);
        Assertions.assertTrue(inspect.getString("stdoutSummary").contains("hello"));
        Assertions.assertTrue(inspect.getString("stderrSummary").contains("oops"));

        JSONObject read = JSONObject.parseObject(readDefinition.invoker().apply(Map.of(
                "id", executionId,
                "limit", 5
        )));
        Assertions.assertEquals("stdout", read.getString("stream"));
        Assertions.assertEquals(0, read.getIntValue("offset"));
        Assertions.assertEquals(5, read.getIntValue("nextOffset"));
        Assertions.assertTrue(read.getBooleanValue("contentTruncated"));
        Assertions.assertEquals("hello", read.getString("content"));

        JSONObject overview = JSONObject.parseObject(overviewDefinition.invoker().apply(Map.of()));
        JSONArray result = overview.getJSONArray("result");
        Assertions.assertTrue(result.stream().map(item -> (JSONObject) item)
                .anyMatch(item -> executionId.equals(item.getString("executionId"))));
    }

    @Test
    void testCancelStopsBackgroundCommand() throws Exception {
        BuiltinCommandActionProvider provider = new BuiltinCommandActionProvider();
        List<BuiltinActionRegistry.BuiltinActionDefinition> definitions = provider.provideBuiltinActions();
        BuiltinActionRegistry.BuiltinActionDefinition start = requireDefinition(definitions, "builtin::command::start");
        BuiltinActionRegistry.BuiltinActionDefinition cancelDefinition = requireDefinition(definitions, "builtin::command::cancel");
        BuiltinActionRegistry.BuiltinActionDefinition inspectDefinition = requireDefinition(definitions, "builtin::command::inspect");

        String startResult = start.invoker().apply(Map.of(
                "desc", "sleep-session",
                "arg", "sh",
                "arg1", "-lc",
                "arg2", "sleep 5"
        ));
        String executionId = JSONObject.parseObject(startResult).getString("executionId");

        JSONObject cancel = JSONObject.parseObject(cancelDefinition.invoker().apply(Map.of(
                "id", executionId
        )));
        Assertions.assertEquals(executionId, cancel.getString("executionId"));
        Assertions.assertTrue(cancel.getBooleanValue("ok"));

        JSONObject inspect = waitForInspectExit(inspectDefinition, executionId);
        Assertions.assertNotNull(inspect.get("endAt"));
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
