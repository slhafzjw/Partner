package work.slhaf.partner.core.action;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import work.slhaf.partner.core.action.entity.*;
import work.slhaf.partner.core.action.exception.ActionLookupException;
import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.action.executor.entity.HistoryAction;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ActionCoreTest {

    private static ActionCore actionCore;

    @BeforeAll
    static void beforeAll(@TempDir Path tempDir) throws Exception {
        System.setProperty("user.home", tempDir.toAbsolutePath().toString());
        actionCore = new ActionCore();
    }

    private static JSONObject buildImmediateActionJson() {
        return JSONObject.of(
                "kind", "immediate",
                "uuid", "immediate-uuid",
                "source", "planner",
                "reason", "immediate-reason",
                "description", "run immediately",
                "status", "EXECUTING",
                "tendency", "urgent",
                "executing_stage", 1,
                "result", "immediate-result",
                "action_chain", JSONArray.of(
                        JSONObject.of(
                                "stage", 1,
                                "actions", JSONArray.of(
                                        JSONObject.of(
                                                "name", "command",
                                                "io", true,
                                                "type", "BUILTIN",
                                                "location", "builtin",
                                                "params_json", "{\"name\":\"demo\",\"count\":2}",
                                                "result_status", "SUCCESS",
                                                "result_data", "done"
                                        )
                                )
                        )
                ),
                "history", JSONArray.of(
                        JSONObject.of(
                                "stage", 1,
                                "actions", JSONArray.of(
                                        JSONObject.of(
                                                "action_key", "builtin::command",
                                                "description", "command finished",
                                                "result", "done"
                                        )
                                )
                        )
                )
        );
    }

    private static JSONObject buildSchedulableActionJson() {
        return JSONObject.of(
                "kind", "schedulable",
                "uuid", "schedulable-uuid",
                "source", "scheduler",
                "reason", "sched-summary",
                "description", "refresh state",
                "status", "PREPARE",
                "tendency", "steady",
                "executing_stage", 0,
                "schedule_type", "CYCLE",
                "schedule_content", "0 0/5 * * * ?",
                "enabled", false,
                "action_chain", JSONArray.of(
                        JSONObject.of(
                                "stage", 2,
                                "actions", JSONArray.of(
                                        JSONObject.of(
                                                "name", "refresh",
                                                "io", false,
                                                "launcher", "bash",
                                                "type", "ORIGIN",
                                                "location", "origin",
                                                "params_json", "{\"interval\":\"5m\"}",
                                                "result_status", "WAITING"
                                        )
                                )
                        )
                ),
                "history", JSONArray.of(),
                "schedule_histories", JSONArray.of(
                        JSONObject.of(
                                "end_time", "2026-04-07T09:30:00+08:00[Asia/Shanghai]",
                                "result", "cycle-result",
                                "history", JSONArray.of(
                                        JSONObject.of(
                                                "stage", 3,
                                                "actions", JSONArray.of(
                                                        JSONObject.of(
                                                                "action_key", "origin::refresh",
                                                                "description", "refresh finished",
                                                                "result", "ok"
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static Map<Integer, List<MetaAction>> actionChain(MetaAction metaAction) {
        Map<Integer, List<MetaAction>> actionChain = new LinkedHashMap<>();
        actionChain.put(1, new ArrayList<>(List.of(metaAction)));
        return actionChain;
    }

    private static MetaAction metaAction(String name,
                                         MetaAction.Type type,
                                         String location,
                                         Map<String, Object> params,
                                         MetaAction.Result.Status status,
                                         String resultData) {
        MetaAction metaAction = new MetaAction(name, true, null, type, location);
        metaAction.getParams().putAll(params);
        metaAction.getResult().setStatus(status);
        metaAction.getResult().setData(resultData);
        return metaAction;
    }

    @BeforeEach
    void setUp() {
        actionCore.load(JSONObject.of("action_pool", new JSONArray()));
    }

    @Test
    void shouldLoadActionPoolAndPreserveUuid() {
        actionCore.load(JSONObject.of("action_pool", JSONArray.of(
                buildImmediateActionJson(),
                buildSchedulableActionJson()
        )));

        Set<work.slhaf.partner.core.action.entity.ExecutableAction> actions = actionCore.listActions(null, null);
        assertEquals(2, actions.size());

        work.slhaf.partner.core.action.entity.ExecutableAction immediate = actions.stream()
                .filter(action -> "immediate-uuid".equals(action.getUuid()))
                .findFirst()
                .orElseThrow();
        assertInstanceOf(ImmediateExecutableAction.class, immediate);
        assertEquals(Action.Status.EXECUTING, immediate.getStatus());
        assertEquals(1, immediate.getExecutingStage());
        assertEquals("immediate-result", immediate.getResult());
        assertEquals(1, immediate.getActionChain().size());
        MetaAction firstMetaAction = immediate.getActionChain().get(1).getFirst();
        assertEquals("builtin::command", firstMetaAction.getKey());
        assertEquals("demo", firstMetaAction.getParams().get("name"));
        assertEquals(2, firstMetaAction.getParams().get("count"));
        assertEquals(MetaAction.Result.Status.SUCCESS, firstMetaAction.getResult().getStatus());
        assertEquals("done", firstMetaAction.getResult().getData());
        HistoryAction historyAction = immediate.getHistory().get(1).getFirst();
        assertEquals("builtin::command", historyAction.actionKey());
        assertEquals("command finished", historyAction.description());
        assertEquals("done", historyAction.result());

        work.slhaf.partner.core.action.entity.ExecutableAction schedulable = actions.stream()
                .filter(action -> "schedulable-uuid".equals(action.getUuid()))
                .findFirst()
                .orElseThrow();
        SchedulableExecutableAction schedulableAction = assertInstanceOf(SchedulableExecutableAction.class, schedulable);
        assertEquals(Action.Status.PREPARE, schedulableAction.getStatus());
        assertEquals("0 0/5 * * * ?", schedulableAction.getScheduleContent());
        assertEquals(Schedulable.ScheduleType.CYCLE, schedulableAction.getScheduleType());
        assertFalse(schedulableAction.getEnabled());
        assertEquals("sched-summary", schedulableAction.getReason());
        assertEquals(1, schedulableAction.getScheduleHistories().size());
        SchedulableExecutableAction.ScheduleHistory scheduleHistory = schedulableAction.getScheduleHistories().getFirst();
        assertEquals(ZonedDateTime.parse("2026-04-07T09:30:00+08:00[Asia/Shanghai]"), scheduleHistory.getEndTime());
        assertEquals("cycle-result", scheduleHistory.getResult());
        HistoryAction scheduledHistoryAction = scheduleHistory.getHistory().get(3).getFirst();
        assertEquals("origin::refresh", scheduledHistoryAction.actionKey());
        assertEquals("refresh finished", scheduledHistoryAction.description());
        assertEquals("ok", scheduledHistoryAction.result());
    }

    @Test
    void shouldConvertActionPoolToState() {
        ImmediateExecutableAction immediateAction = new ImmediateExecutableAction(
                "urgent",
                actionChain(metaAction("command", MetaAction.Type.BUILTIN, "builtin", Map.of("name", "demo"), MetaAction.Result.Status.SUCCESS, "done")),
                "immediate-reason",
                "run immediately",
                "planner",
                "immediate-uuid"
        );
        immediateAction.setStatus(Action.Status.EXECUTING);
        immediateAction.setExecutingStage(1);
        immediateAction.setResult("immediate-result");
        immediateAction.getHistory().put(1, new ArrayList<>(List.of(new HistoryAction("builtin::command", "command finished", "done"))));

        SchedulableExecutableAction schedulableAction = new SchedulableExecutableAction(
                "steady",
                actionChain(metaAction("refresh", MetaAction.Type.ORIGIN, "origin", Map.of("interval", "5m"), MetaAction.Result.Status.WAITING, null)),
                "sched-summary",
                "refresh state",
                "scheduler",
                Schedulable.ScheduleType.CYCLE,
                "0 0/5 * * * ?",
                "schedulable-uuid"
        );
        schedulableAction.setEnabled(false);
        schedulableAction.setStatus(Action.Status.PREPARE);
        schedulableAction.getScheduleHistories().add(new SchedulableExecutableAction.ScheduleHistory(
                ZonedDateTime.parse("2026-04-07T09:30:00+08:00[Asia/Shanghai]"),
                "cycle-result",
                Map.of(3, List.of(new HistoryAction("origin::refresh", "refresh finished", "ok")))
        ));

        actionCore.putAction(immediateAction);
        actionCore.putAction(schedulableAction);

        JSONObject state = JSONObject.parseObject(actionCore.convert().toString());
        JSONArray actionPool = state.getJSONArray("action_pool");
        assertNotNull(actionPool);
        assertEquals(2, actionPool.size());

        JSONObject immediateJson = actionPool.stream()
                .map(JSONObject.class::cast)
                .filter(item -> "immediate-uuid".equals(item.getString("uuid")))
                .findFirst()
                .orElseThrow();
        assertEquals("immediate", immediateJson.getString("kind"));
        assertEquals("planner", immediateJson.getString("source"));
        assertEquals("urgent", immediateJson.getString("tendency"));
        assertEquals("EXECUTING", immediateJson.getString("status"));
        assertEquals(1, immediateJson.getIntValue("executing_stage"));
        assertEquals("immediate-result", immediateJson.getString("result"));
        JSONArray immediateChain = immediateJson.getJSONArray("action_chain");
        assertEquals(1, immediateChain.size());
        JSONObject immediateStage = immediateChain.getJSONObject(0);
        assertEquals(1, immediateStage.getIntValue("stage"));
        JSONObject immediateMeta = immediateStage.getJSONArray("actions").getJSONObject(0);
        assertEquals("command", immediateMeta.getString("name"));
        assertEquals("builtin", immediateMeta.getString("location"));
        assertEquals("SUCCESS", immediateMeta.getString("result_status"));
        assertEquals("done", immediateMeta.getString("result_data"));
        assertEquals("{\"name\":\"demo\"}", immediateMeta.getString("params_json"));

        JSONObject schedulableJson = actionPool.stream()
                .map(JSONObject.class::cast)
                .filter(item -> "schedulable-uuid".equals(item.getString("uuid")))
                .findFirst()
                .orElseThrow();
        assertEquals("schedulable", schedulableJson.getString("kind"));
        assertEquals("CYCLE", schedulableJson.getString("schedule_type"));
        assertEquals("0 0/5 * * * ?", schedulableJson.getString("schedule_content"));
        assertFalse(schedulableJson.getBooleanValue("enabled"));
        assertNull(schedulableJson.getString("result"));
        JSONArray scheduleHistories = schedulableJson.getJSONArray("schedule_histories");
        assertNotNull(scheduleHistories);
        assertEquals(1, scheduleHistories.size());
        JSONObject scheduleHistory = scheduleHistories.getJSONObject(0);
        assertEquals("2026-04-07T09:30+08:00[Asia/Shanghai]", scheduleHistory.getString("end_time"));
        assertEquals("cycle-result", scheduleHistory.getString("result"));
        JSONObject scheduleStage = scheduleHistory.getJSONArray("history").getJSONObject(0);
        assertEquals(3, scheduleStage.getIntValue("stage"));
        JSONObject scheduledHistory = scheduleStage.getJSONArray("actions").getJSONObject(0);
        assertEquals("origin::refresh", scheduledHistory.getString("action_key"));
        assertEquals("refresh finished", scheduledHistory.getString("description"));
        assertEquals("ok", scheduledHistory.getString("result"));
    }

    @Test
    void shouldResetToEmptyPoolWhenActionPoolMissing() {
        actionCore.putAction(new ImmediateExecutableAction(
                "urgent",
                new LinkedHashMap<>(),
                "reason",
                "description",
                "planner",
                "transient-uuid"
        ));

        actionCore.load(JSONObject.of());

        assertTrue(actionCore.listActions(null, null).isEmpty());
    }

    @Test
    void shouldSkipInvalidScheduleHistoryEntriesDuringLoad() {
        JSONObject schedulableJson = buildSchedulableActionJson();
        schedulableJson.put("schedule_histories", JSONArray.of(
                JSONObject.of(
                        "end_time", "2026-04-07T09:30:00+08:00[Asia/Shanghai]",
                        "result", "cycle-result",
                        "history", JSONArray.of(
                                JSONObject.of(
                                        "stage", 3,
                                        "actions", JSONArray.of(
                                                JSONObject.of(
                                                        "action_key", "origin::refresh",
                                                        "description", "refresh finished",
                                                        "result", "ok"
                                                )
                                        )
                                )
                        )
                ),
                JSONObject.of(
                        "end_time", "bad-time",
                        "result", "broken",
                        "history", JSONArray.of()
                )
        ));

        actionCore.load(JSONObject.of("action_pool", JSONArray.of(schedulableJson)));

        SchedulableExecutableAction schedulableAction = assertInstanceOf(
                SchedulableExecutableAction.class,
                actionCore.listActions(null, null).iterator().next()
        );
        assertEquals(1, schedulableAction.getScheduleHistories().size());
        assertEquals("cycle-result", schedulableAction.getScheduleHistories().getFirst().getResult());
    }

    @Test
    void shouldReturnResultForMetaActionLookup() {
        MetaActionInfo metaActionInfo = new MetaActionInfo(
                true,
                "python",
                Map.of(),
                "demo",
                Set.of(),
                Set.of(),
                Set.of(),
                false,
                JSONObject.of()
        );
        actionCore.registerMetaActions(Map.of("builtin::demo", metaActionInfo));

        Result<MetaAction> success = actionCore.loadMetaAction("builtin::demo");
        assertEquals("demo", success.fold(MetaAction::getName, ex -> fail(ex.getMessage())));

        Result<MetaAction> failure = actionCore.loadMetaAction("builtin::missing");
        assertInstanceOf(ActionLookupException.class, assertThrows(AgentRuntimeException.class, failure::getOrThrow));

        Result<MetaActionInfo> infoFailure = actionCore.loadMetaActionInfo("builtin::missing");
        assertInstanceOf(ActionLookupException.class, assertThrows(AgentRuntimeException.class, infoFailure::getOrThrow));
    }
}
