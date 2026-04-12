package work.slhaf.partner.module.action.executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.*;
import work.slhaf.partner.core.action.runner.RunnerClient;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextWorkspace;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.action.executor.entity.ExtractorResult;
import work.slhaf.partner.module.action.executor.entity.HistoryAction;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ActionExecutorTest {

    private final List<ExecutorService> executors = new ArrayList<>();

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = ActionExecutor.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Map<Integer, List<MetaAction>> actionChain(MetaAction metaAction) {
        Map<Integer, List<MetaAction>> actionChain = new LinkedHashMap<>();
        actionChain.put(1, new ArrayList<>(List.of(metaAction)));
        return actionChain;
    }

    private static MetaAction metaAction(String name) {
        return new MetaAction(name, false, null, MetaAction.Type.BUILTIN, "builtin");
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        for (ExecutorService executor : executors) {
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldResumeExecutingImmediateAndSchedulableActionsOnInit() throws Exception {
        ActionCapability actionCapability = Mockito.mock(ActionCapability.class);
        CognitionCapability cognitionCapability = Mockito.mock(CognitionCapability.class);

        ExecutorService virtualExecutor = registerExecutor(Executors.newFixedThreadPool(2));
        ExecutorService platformExecutor = registerExecutor(Executors.newFixedThreadPool(2));
        RunnerClient runnerClient = Mockito.mock(RunnerClient.class);

        ImmediateExecutableAction immediateAction = new ImmediateExecutableAction(
                "urgent",
                actionChain(metaAction("command")),
                "reason",
                "desc",
                "planner",
                "immediate-uuid"
        );
        immediateAction.setStatus(Action.Status.EXECUTING);
        immediateAction.setExecutingStage(1);

        SchedulableExecutableAction schedulableAction = new SchedulableExecutableAction(
                "steady",
                actionChain(metaAction("refresh")),
                "reason",
                "desc",
                "scheduler",
                Schedulable.ScheduleType.CYCLE,
                "0 0/5 * * * ?",
                "schedulable-uuid"
        );
        schedulableAction.setStatus(Action.Status.EXECUTING);
        schedulableAction.setExecutingStage(1);

        when(actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL)).thenReturn(virtualExecutor);
        when(actionCapability.getExecutor(ActionCore.ExecutorType.PLATFORM)).thenReturn(platformExecutor);
        when(actionCapability.runnerClient()).thenReturn(runnerClient);
        when(actionCapability.listActions(Action.Status.EXECUTING, null)).thenReturn(Set.of(immediateAction, schedulableAction));
        when(cognitionCapability.contextWorkspace()).thenReturn(new ContextWorkspace());

        ActionExecutor actionExecutor = spy(new ActionExecutor());
        inject(actionExecutor, "actionCapability", actionCapability);
        inject(actionExecutor, "cognitionCapability", cognitionCapability);
        inject(actionExecutor, "paramsExtractor", Mockito.mock(ParamsExtractor.class));
        inject(actionExecutor, "actionCorrector", Mockito.mock(ActionCorrector.class));
        inject(actionExecutor, "actionCorrectionRecognizer", Mockito.mock(ActionCorrectionRecognizer.class));
        doNothing().when(actionExecutor).execute(any(Action.class));

        actionExecutor.init();

        verify(actionExecutor, times(1)).execute(immediateAction);
        verify(actionExecutor, times(1)).execute(schedulableAction);
    }

    @Test
    void shouldReplayFromFirstStageWhenExecutingStageIsInvalid() throws Exception {
        ActionCapability actionCapability = Mockito.mock(ActionCapability.class);
        CognitionCapability cognitionCapability = Mockito.mock(CognitionCapability.class);
        ParamsExtractor paramsExtractor = Mockito.mock(ParamsExtractor.class);
        ActionCorrector actionCorrector = Mockito.mock(ActionCorrector.class);
        ActionCorrectionRecognizer actionCorrectionRecognizer = Mockito.mock(ActionCorrectionRecognizer.class);
        RunnerClient runnerClient = Mockito.mock(RunnerClient.class);

        ExecutorService virtualExecutor = registerExecutor(Executors.newFixedThreadPool(2));
        ExecutorService platformExecutor = registerExecutor(Executors.newFixedThreadPool(2));

        when(actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL)).thenReturn(virtualExecutor);
        when(actionCapability.getExecutor(ActionCore.ExecutorType.PLATFORM)).thenReturn(platformExecutor);
        when(actionCapability.runnerClient()).thenReturn(runnerClient);
        when(actionCapability.listActions(Action.Status.EXECUTING, null)).thenReturn(Set.of());
        when(actionCapability.loadMetaActionInfo(anyString())).thenReturn(Result.success(new MetaActionInfo(
                false,
                null,
                Map.of(),
                "demo action",
                Set.of(),
                Set.of(),
                Set.of(),
                false,
                new com.alibaba.fastjson2.JSONObject()
        )));
        when(cognitionCapability.contextWorkspace()).thenReturn(new ContextWorkspace());

        ExtractorResult extractorResult = new ExtractorResult();
        extractorResult.setOk(true);
        extractorResult.setParams(Map.of("fresh", "value"));
        when(paramsExtractor.execute(any())).thenReturn(Result.success(extractorResult));
        doAnswer(invocation -> {
            MetaAction metaAction = invocation.getArgument(0);
            metaAction.getResult().setStatus(MetaAction.Result.Status.SUCCESS);
            metaAction.getResult().setData("rerun-ok");
            return null;
        }).when(runnerClient).submit(any(MetaAction.class));

        ActionExecutor actionExecutor = new ActionExecutor();
        inject(actionExecutor, "actionCapability", actionCapability);
        inject(actionExecutor, "cognitionCapability", cognitionCapability);
        inject(actionExecutor, "paramsExtractor", paramsExtractor);
        inject(actionExecutor, "actionCorrector", actionCorrector);
        inject(actionExecutor, "actionCorrectionRecognizer", actionCorrectionRecognizer);
        actionExecutor.init();

        MetaAction metaAction = metaAction("command");
        metaAction.getParams().put("stale", "old");
        metaAction.getResult().setStatus(MetaAction.Result.Status.FAILED);
        metaAction.getResult().setData("stale-meta");

        ImmediateExecutableAction action = new ImmediateExecutableAction(
                "urgent",
                actionChain(metaAction),
                "reason",
                "desc",
                "planner",
                "replay-uuid"
        );
        action.setStatus(Action.Status.EXECUTING);
        action.setExecutingStage(99);
        action.setResult("stale-result");
        action.getHistory().put(9, new ArrayList<>(List.of(new HistoryAction("old::action", "stale", "bad"))));

        actionExecutor.execute(action);

        waitUntilFinished(action);

        assertEquals(Action.Status.SUCCESS, action.getStatus());
        assertEquals(1, action.getExecutingStage());
        assertEquals("rerun-ok", action.getResult());
        assertEquals(1, action.getHistory().size());
        assertTrue(action.getHistory().containsKey(1));
        assertEquals("rerun-ok", action.getHistory().get(1).getFirst().result());
        assertEquals(Map.of("fresh", "value"), metaAction.getParams());
        assertEquals(MetaAction.Result.Status.SUCCESS, metaAction.getResult().getStatus());
        assertEquals("rerun-ok", metaAction.getResult().getData());
    }

    @Test
    void shouldMarkMetaActionFailedWhenMetaActionInfoLookupFailsBeforeExtraction() throws Exception {
        ActionCapability actionCapability = Mockito.mock(ActionCapability.class);
        CognitionCapability cognitionCapability = Mockito.mock(CognitionCapability.class);
        ParamsExtractor paramsExtractor = Mockito.mock(ParamsExtractor.class);
        ActionCorrector actionCorrector = Mockito.mock(ActionCorrector.class);
        ActionCorrectionRecognizer actionCorrectionRecognizer = Mockito.mock(ActionCorrectionRecognizer.class);
        RunnerClient runnerClient = Mockito.mock(RunnerClient.class);

        ExecutorService virtualExecutor = registerExecutor(Executors.newFixedThreadPool(2));
        ExecutorService platformExecutor = registerExecutor(Executors.newFixedThreadPool(2));

        when(actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL)).thenReturn(virtualExecutor);
        when(actionCapability.getExecutor(ActionCore.ExecutorType.PLATFORM)).thenReturn(platformExecutor);
        when(actionCapability.runnerClient()).thenReturn(runnerClient);
        when(actionCapability.listActions(Action.Status.EXECUTING, null)).thenReturn(Set.of());
        when(actionCapability.loadMetaActionInfo(anyString())).thenReturn(Result.failure(new work.slhaf.partner.core.action.exception.ActionLookupException(
                "missing",
                "builtin::command",
                "META_ACTION_INFO"
        )));
        when(cognitionCapability.contextWorkspace()).thenReturn(new ContextWorkspace());
        work.slhaf.partner.module.action.executor.entity.CorrectorResult correctorResult =
                new work.slhaf.partner.module.action.executor.entity.CorrectorResult();
        correctorResult.setMetaInterventionList(List.of());
        correctorResult.setCorrectionReason("no-op");
        when(actionCorrector.execute(any())).thenReturn(Result.success(correctorResult));

        ActionExecutor actionExecutor = new ActionExecutor();
        inject(actionExecutor, "actionCapability", actionCapability);
        inject(actionExecutor, "cognitionCapability", cognitionCapability);
        inject(actionExecutor, "paramsExtractor", paramsExtractor);
        inject(actionExecutor, "actionCorrector", actionCorrector);
        inject(actionExecutor, "actionCorrectionRecognizer", actionCorrectionRecognizer);
        actionExecutor.init();

        MetaAction metaAction = metaAction("command");
        ImmediateExecutableAction action = new ImmediateExecutableAction(
                "urgent",
                actionChain(metaAction),
                "reason",
                "desc",
                "planner",
                "lookup-fail-uuid"
        );

        actionExecutor.execute(action);
        waitUntilFinished(action);

        verify(paramsExtractor, never()).execute(any());
        verify(runnerClient, never()).submit(any(MetaAction.class));
        assertEquals(Action.Status.SUCCESS, action.getStatus());
        assertEquals(MetaAction.Result.Status.FAILED, metaAction.getResult().getStatus());
        assertTrue(metaAction.getResult().getData().contains("missing"));
        assertEquals(metaAction.getResult().getData(), action.getResult());
    }

    @Test
    void shouldFallbackToActionKeyWhenHistoryDescriptionLookupFails() throws Exception {
        ActionCapability actionCapability = Mockito.mock(ActionCapability.class);
        CognitionCapability cognitionCapability = Mockito.mock(CognitionCapability.class);
        ParamsExtractor paramsExtractor = Mockito.mock(ParamsExtractor.class);
        ActionCorrector actionCorrector = Mockito.mock(ActionCorrector.class);
        ActionCorrectionRecognizer actionCorrectionRecognizer = Mockito.mock(ActionCorrectionRecognizer.class);
        RunnerClient runnerClient = Mockito.mock(RunnerClient.class);

        ExecutorService virtualExecutor = registerExecutor(Executors.newFixedThreadPool(2));
        ExecutorService platformExecutor = registerExecutor(Executors.newFixedThreadPool(2));

        when(actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL)).thenReturn(virtualExecutor);
        when(actionCapability.getExecutor(ActionCore.ExecutorType.PLATFORM)).thenReturn(platformExecutor);
        when(actionCapability.runnerClient()).thenReturn(runnerClient);
        when(actionCapability.listActions(Action.Status.EXECUTING, null)).thenReturn(Set.of());
        when(cognitionCapability.contextWorkspace()).thenReturn(new ContextWorkspace());

        when(actionCapability.loadMetaActionInfo(anyString()))
                .thenReturn(Result.success(new MetaActionInfo(
                        false,
                        null,
                        Map.of(),
                        "demo action",
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        false,
                        new com.alibaba.fastjson2.JSONObject()
                )))
                .thenReturn(Result.failure(new work.slhaf.partner.core.action.exception.ActionLookupException(
                        "missing desc",
                        "builtin::command",
                        "META_ACTION_INFO"
                )));

        ExtractorResult extractorResult = new ExtractorResult();
        extractorResult.setOk(true);
        extractorResult.setParams(Map.of("fresh", "value"));
        when(paramsExtractor.execute(any())).thenReturn(Result.success(extractorResult));
        doAnswer(invocation -> {
            MetaAction metaAction = invocation.getArgument(0);
            metaAction.getResult().setStatus(MetaAction.Result.Status.SUCCESS);
            metaAction.getResult().setData("history-ok");
            return null;
        }).when(runnerClient).submit(any(MetaAction.class));

        ActionExecutor actionExecutor = new ActionExecutor();
        inject(actionExecutor, "actionCapability", actionCapability);
        inject(actionExecutor, "cognitionCapability", cognitionCapability);
        inject(actionExecutor, "paramsExtractor", paramsExtractor);
        inject(actionExecutor, "actionCorrector", actionCorrector);
        inject(actionExecutor, "actionCorrectionRecognizer", actionCorrectionRecognizer);
        actionExecutor.init();

        MetaAction metaAction = metaAction("command");
        ImmediateExecutableAction action = new ImmediateExecutableAction(
                "urgent",
                actionChain(metaAction),
                "reason",
                "desc",
                "planner",
                "history-fallback-uuid"
        );

        actionExecutor.execute(action);
        waitUntilFinished(action);

        assertEquals(Action.Status.SUCCESS, action.getStatus());
        assertEquals("builtin::command", action.getHistory().get(1).getFirst().description());
    }

    private void waitUntilFinished(ImmediateExecutableAction action) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (action.getStatus() == Action.Status.SUCCESS || action.getStatus() == Action.Status.FAILED) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("action execution did not finish in time");
    }

    private ExecutorService registerExecutor(ExecutorService executorService) {
        executors.add(executorService);
        return executorService;
    }
}
