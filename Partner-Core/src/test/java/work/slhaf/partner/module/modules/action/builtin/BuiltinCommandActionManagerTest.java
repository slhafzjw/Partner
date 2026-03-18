package work.slhaf.partner.module.modules.action.builtin;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class BuiltinCommandActionManagerTest {

    @Test
    void testStartInspectReadAndOverview() throws Exception {
        BuiltinCommandActionManager manager = new BuiltinCommandActionManager();

        String startResult = manager.buildCommandStartDefinition().invoker().apply(Map.of(
                "desc", "demo-session",
                "arg", "sh",
                "arg1", "-lc",
                "arg2", "printf 'hello\\nworld\\n'; printf 'oops\\n' >&2"
        ));
        String executionId = JSONObject.parseObject(startResult).getString("executionId");
        Assertions.assertNotNull(executionId);

        JSONObject inspect = waitForInspectExit(manager, executionId);
        Assertions.assertEquals("demo-session", inspect.getString("desc"));
        Assertions.assertEquals(0, inspect.getInteger("exitCode"));
        Assertions.assertTrue(inspect.getInteger("stdoutSize") > 0);
        Assertions.assertTrue(inspect.getInteger("stderrSize") > 0);
        Assertions.assertTrue(inspect.getString("stdoutSummary").contains("hello"));
        Assertions.assertTrue(inspect.getString("stderrSummary").contains("oops"));

        JSONObject read = JSONObject.parseObject(manager.buildCommandReadDefinition().invoker().apply(Map.of(
                "id", executionId,
                "limit", 5
        )));
        Assertions.assertEquals("stdout", read.getString("stream"));
        Assertions.assertEquals(0, read.getIntValue("offset"));
        Assertions.assertEquals(5, read.getIntValue("nextOffset"));
        Assertions.assertTrue(read.getBooleanValue("contentTruncated"));
        Assertions.assertEquals("hello", read.getString("content"));

        JSONObject overview = JSONObject.parseObject(manager.buildCommandOverviewDefinition().invoker().apply(Map.of()));
        JSONArray result = overview.getJSONArray("result");
        Assertions.assertTrue(result.stream().map(item -> (JSONObject) item)
                .anyMatch(item -> executionId.equals(item.getString("executionId"))));
    }

    @Test
    void testCancelStopsBackgroundCommand() throws Exception {
        BuiltinCommandActionManager manager = new BuiltinCommandActionManager();

        String startResult = manager.buildCommandStartDefinition().invoker().apply(Map.of(
                "desc", "sleep-session",
                "arg", "sh",
                "arg1", "-lc",
                "arg2", "sleep 5"
        ));
        String executionId = JSONObject.parseObject(startResult).getString("executionId");

        JSONObject cancel = JSONObject.parseObject(manager.buildCommandCancelDefinition().invoker().apply(Map.of(
                "id", executionId
        )));
        Assertions.assertEquals(executionId, cancel.getString("executionId"));
        Assertions.assertTrue(cancel.getBooleanValue("ok"));

        JSONObject inspect = waitForInspectExit(manager, executionId);
        Assertions.assertNotNull(inspect.get("endAt"));
    }

    private JSONObject waitForInspectExit(BuiltinCommandActionManager manager, String executionId) throws Exception {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            JSONObject inspect = JSONObject.parseObject(manager.buildCommandInspectDefinition().invoker().apply(Map.of(
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
