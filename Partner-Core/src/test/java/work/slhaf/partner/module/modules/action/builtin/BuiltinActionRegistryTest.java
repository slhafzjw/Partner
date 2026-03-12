package work.slhaf.partner.module.modules.action.builtin;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.exception.MetaActionNotFoundException;
import work.slhaf.partner.core.action.runner.RunnerClient;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

class BuiltinActionRegistryTest {

    private static void injectActionCapability(BuiltinActionRegistry registry, ActionCapability actionCapability) throws Exception {
        Field field = BuiltinActionRegistry.class.getDeclaredField("actionCapability");
        field.setAccessible(true);
        field.set(registry, actionCapability);
    }

    private static Map<String, BuiltinActionRegistry.BuiltinActionDefinition> indexDefinitions(
            List<BuiltinActionRegistry.BuiltinActionDefinition> definitions
    ) {
        Map<String, BuiltinActionRegistry.BuiltinActionDefinition> map = new HashMap<>();
        for (BuiltinActionRegistry.BuiltinActionDefinition definition : definitions) {
            map.put(definition.actionKey(), definition);
        }
        return map;
    }

    private static MetaActionInfo buildMetaActionInfo(String description) {
        MetaActionInfo info = new MetaActionInfo();
        info.setDescription(description);
        info.setParams(new HashMap<>());
        return info;
    }

    @Test
    void testInitRegistersMetaActionsAndMountsRunner() throws Exception {
        ActionCapability actionCapability = mock(ActionCapability.class);
        RunnerClient runnerClient = mock(RunnerClient.class);
        when(actionCapability.runnerClient()).thenReturn(runnerClient);

        BuiltinActionRegistry registry = new TestRegistry(List.of(
                BuiltinActionRegistry.definition("echo", buildMetaActionInfo("echo"), params -> params.get("value"))
        ));
        injectActionCapability(registry, actionCapability);

        registry.init();

        verify(actionCapability).registerMetaActions(argThat(metaActions ->
                metaActions.containsKey("builtin::echo")
                        && "echo".equals(metaActions.get("builtin::echo").getDescription())
        ));
        verify(runnerClient).setBuiltinActionRegistry(registry);
    }

    @Test
    void testCallReturnsStringifiedResults() {
        BuiltinActionRegistry registry = new TestRegistry(List.of(
                BuiltinActionRegistry.definition("echo", buildMetaActionInfo("echo"), params -> params.get("value")),
                BuiltinActionRegistry.definition("json", buildMetaActionInfo("json"), params -> Map.of("ok", true)),
                BuiltinActionRegistry.definition("nil", buildMetaActionInfo("nil"), params -> null)
        ));

        registry.getDefinitions().putAll(indexDefinitions(registry.buildDefinitions()));

        Assertions.assertEquals("hello", registry.call("builtin::echo", Map.of("value", "hello")));
        Assertions.assertEquals("{\"ok\":true}", registry.call("builtin::json", Map.of()));
        Assertions.assertEquals("null", registry.call("builtin::nil", Map.of()));
    }

    @Test
    void testCallThrowsWhenMissingDefinition() {
        BuiltinActionRegistry registry = new TestRegistry(List.of());
        Assertions.assertThrows(MetaActionNotFoundException.class, () -> registry.call("builtin::missing", Map.of()));
    }

    @Test
    void testCallPropagatesInvokerFailure() {
        BuiltinActionRegistry registry = new TestRegistry(List.of(
                BuiltinActionRegistry.definition("boom", buildMetaActionInfo("boom"), params -> {
                    throw new IllegalStateException("boom");
                })
        ));
        registry.getDefinitions().putAll(indexDefinitions(registry.buildDefinitions()));

        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
                () -> registry.call("builtin::boom", Map.of()));
        Assertions.assertEquals("boom", exception.getMessage());
    }

    @Test
    void testActionCoreLoadsBuiltinMetaAction() throws Exception {
        ActionCore actionCore = new ActionCore();
        try {
            actionCore.registerMetaActions(Map.of("builtin::echo", buildMetaActionInfo("echo")));

            Assertions.assertTrue(actionCore.listAvailableMetaActions().containsKey("builtin::echo"));
            Assertions.assertEquals("echo", actionCore.loadMetaActionInfo("builtin::echo").getDescription());
            Assertions.assertEquals("builtin::echo", actionCore.loadMetaAction("builtin::echo").getKey());
            Assertions.assertEquals(ActionCore.BUILTIN_LOCATION, actionCore.loadMetaAction("builtin::echo").getLocation());
        } finally {
            actionCore.getExecutor(ActionCore.ExecutorType.PLATFORM).shutdownNow();
            actionCore.getExecutor(ActionCore.ExecutorType.VIRTUAL).shutdownNow();
        }
    }

    private static class TestRegistry extends BuiltinActionRegistry {
        private final List<BuiltinActionDefinition> definitions;

        private TestRegistry(List<BuiltinActionDefinition> definitions) {
            this.definitions = definitions;
        }

        @Override
        protected List<BuiltinActionDefinition> buildDefinitions() {
            return definitions;
        }
    }
}
