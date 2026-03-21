package work.slhaf.partner.module.modules.action.builtin;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.entity.ActionFileMetaData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.entity.StateAction;
import work.slhaf.partner.core.action.runner.RunnerClient;
import work.slhaf.partner.module.modules.action.scheduler.ActionScheduler;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

class BuiltinDynamicActionProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void generateShouldRegisterTempOriginActionAndScheduleCleanup() throws Exception {
        TestContext context = createContext();
        BuiltinActionRegistry.BuiltinActionDefinition generate = requireDefinition(context.provider.provideBuiltinActions(), "builtin::dynamic::generate");

        JSONObject result = JSONObject.parseObject(generate.invoker().apply(Map.of(
                "desc", "temporary origin",
                "code", "print('ok')",
                "codeType", "py",
                "launcher", "python3",
                "meta", """
                        {
                          "io": true,
                          "params": {"input": "user input"},
                          "tags": ["dynamic", "temp"],
                          "preActions": ["builtin::command::execute"],
                          "postActions": ["builtin::dynamic::persist"],
                          "strictDependencies": true,
                          "responseSchema": {"result": "dynamic result"}
                        }
                        """
        )));

        String actionKey = result.getString("actionKey");
        Assertions.assertTrue(result.getBooleanValue("ok"));
        Assertions.assertTrue(actionKey.startsWith("origin::"));

        MetaActionInfo metaActionInfo = context.availableMetaActions.get(actionKey);
        Assertions.assertNotNull(metaActionInfo);
        Assertions.assertEquals("python3", metaActionInfo.getLauncher());
        Assertions.assertEquals("temporary origin", metaActionInfo.getDescription());
        Assertions.assertTrue(metaActionInfo.getIo());
        Assertions.assertEquals("user input", metaActionInfo.getParams().get("input"));
        Assertions.assertTrue(metaActionInfo.getTags().contains("dynamic"));
        Assertions.assertTrue(metaActionInfo.getStrictDependencies());
        Assertions.assertEquals("dynamic result", metaActionInfo.getResponseSchema().getString("result"));

        Path tempFile = Path.of(actionKey.substring("origin::".length()));
        Assertions.assertTrue(Files.exists(tempFile));

        ArgumentCaptor<StateAction> cleanupCaptor = ArgumentCaptor.forClass(StateAction.class);
        Mockito.verify(context.actionScheduler).schedule(cleanupCaptor.capture());
        Assertions.assertEquals("清理临时动态行动", cleanupCaptor.getValue().getDescription());
    }

    @Test
    void persistShouldPersistDeleteTempFileAndCancelCleanup() throws Exception {
        TestContext context = createContext();
        BuiltinActionRegistry.BuiltinActionDefinition generate = requireDefinition(context.provider.provideBuiltinActions(), "builtin::dynamic::generate");
        BuiltinActionRegistry.BuiltinActionDefinition persist = requireDefinition(context.provider.provideBuiltinActions(), "builtin::dynamic::persist");

        String actionKey = JSONObject.parseObject(generate.invoker().apply(Map.of(
                "desc", "persist target",
                "code", "print('persist')",
                "codeType", ".py",
                "launcher", "python3",
                "meta", "{}"
        ))).getString("actionKey");
        Path tempFile = Path.of(actionKey.substring("origin::".length()));

        ArgumentCaptor<StateAction> cleanupCaptor = ArgumentCaptor.forClass(StateAction.class);
        Mockito.verify(context.actionScheduler).schedule(cleanupCaptor.capture());
        StateAction cleanupAction = cleanupCaptor.getValue();

        JSONObject persistResult = JSONObject.parseObject(persist.invoker().apply(Map.of("actionKey", actionKey)));

        Assertions.assertTrue(persistResult.getBooleanValue("ok"));
        Assertions.assertEquals(actionKey, persistResult.getString("actionKey"));
        Assertions.assertTrue(context.runnerClient.persistCalled);
        Assertions.assertFalse(Files.exists(tempFile));
        Assertions.assertFalse(context.availableMetaActions.containsKey(actionKey));
        Mockito.verify(context.actionScheduler).cancel(cleanupAction.getUuid());
    }

    @Test
    void cleanupActionShouldDeleteTempFileAndRemoveMetaAction() throws Exception {
        TestContext context = createContext();
        BuiltinActionRegistry.BuiltinActionDefinition generate = requireDefinition(context.provider.provideBuiltinActions(), "builtin::dynamic::generate");

        String actionKey = JSONObject.parseObject(generate.invoker().apply(Map.of(
                "desc", "cleanup target",
                "code", "print('cleanup')",
                "codeType", "py",
                "launcher", "python3",
                "meta", "{}"
        ))).getString("actionKey");
        Path tempFile = Path.of(actionKey.substring("origin::".length()));

        ArgumentCaptor<StateAction> cleanupCaptor = ArgumentCaptor.forClass(StateAction.class);
        Mockito.verify(context.actionScheduler).schedule(cleanupCaptor.capture());
        cleanupCaptor.getValue().getTrigger().onTrigger();

        Assertions.assertFalse(Files.exists(tempFile));
        Assertions.assertFalse(context.availableMetaActions.containsKey(actionKey));
    }

    private TestContext createContext() throws Exception {
        BuiltinDynamicActionProvider provider = new BuiltinDynamicActionProvider();
        ActionCapability actionCapability = Mockito.mock(ActionCapability.class);
        ActionScheduler actionScheduler = Mockito.mock(ActionScheduler.class);
        Map<String, MetaActionInfo> availableMetaActions = new LinkedHashMap<>();
        TestRunnerClient runnerClient = new TestRunnerClient(tempDir);

        Mockito.when(actionCapability.runnerClient()).thenReturn(runnerClient);
        Mockito.when(actionCapability.listAvailableMetaActions()).thenReturn(availableMetaActions);
        Mockito.doAnswer(invocation -> {
            availableMetaActions.putAll(invocation.getArgument(0));
            return null;
        }).when(actionCapability).registerMetaActions(Mockito.anyMap());

        inject(provider, "actionCapability", actionCapability);
        inject(provider, "actionScheduler", actionScheduler);
        return new TestContext(provider, actionScheduler, availableMetaActions, runnerClient);
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

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record TestContext(
            BuiltinDynamicActionProvider provider,
            ActionScheduler actionScheduler,
            Map<String, MetaActionInfo> availableMetaActions,
            TestRunnerClient runnerClient
    ) {
    }

    private static class TestRunnerClient extends RunnerClient {
        private final Path tempBase;
        private boolean persistCalled;

        private TestRunnerClient(Path tempBase) {
            super(new ConcurrentHashMap<>(), Executors.newSingleThreadExecutor(), tempBase.toString());
            this.tempBase = tempBase;
        }

        @Override
        protected RunnerResponse doRun(MetaAction metaAction) {
            return new RunnerResponse();
        }

        @Override
        public String buildTmpPath(String actionKey, String codeType) {
            String normalized = codeType.startsWith(".") ? codeType : "." + codeType;
            return tempBase.resolve(actionKey + normalized).toString();
        }

        @Override
        public void tmpSerialize(MetaAction tempAction, String code, String codeType) throws IOException {
            Path path = Path.of(tempAction.getLocation());
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, code);
        }

        @Override
        public void persistSerialize(MetaActionInfo metaActionInfo, ActionFileMetaData fileMetaData) {
            persistCalled = true;
        }
    }
}
