package work.slhaf.partner.module.action.builtin;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.exception.ActionLookupException;
import work.slhaf.partner.core.action.runner.RunnerClient;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.*;
import static work.slhaf.partner.core.action.ActionCore.BUILTIN_LOCATION;

class BuiltinActionRegistryTest {

    private static void injectActionCapability(BuiltinActionRegistry registry, ActionCapability actionCapability) throws Exception {
        Field field = BuiltinActionRegistry.class.getDeclaredField("actionCapability");
        field.setAccessible(true);
        field.set(registry, actionCapability);
    }

    private static MetaActionInfo buildMetaActionInfo(String description) {
        return new MetaActionInfo(
                false,
                null,
                new HashMap<>(),
                description,
                Set.of(),
                Set.of(),
                Set.of(),
                false,
                new JSONObject()
        );
    }

    private static BuiltinActionRegistry.BuiltinActionDefinition buildDefinition(
            String name,
            MetaActionInfo metaActionInfo,
            java.util.function.Function<Map<String, Object>, String> invoker
    ) {
        return new BuiltinActionRegistry.BuiltinActionDefinition(
                BUILTIN_LOCATION + "::" + name,
                metaActionInfo,
                invoker
        );
    }

    @Test
    void testInitRegistersMetaActionsAndMountsRunner() throws Exception {
        ActionCapability actionCapability = mock(ActionCapability.class);
        RunnerClient runnerClient = mock(RunnerClient.class);
        when(actionCapability.runnerClient()).thenReturn(runnerClient);

        BuiltinActionRegistry registry = spy(new BuiltinActionRegistry());
        doReturn(List.of(
                buildDefinition("echo", buildMetaActionInfo("echo"), params -> params.get("value").toString())
        )).when(registry).buildDefaultActionDefinitions();
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
        BuiltinActionRegistry registry = new BuiltinActionRegistry();
        registry.defineBuiltinAction("echo", buildMetaActionInfo("echo"), params -> params.get("value").toString());
        registry.defineBuiltinAction("json", buildMetaActionInfo("json"), params -> Map.of("ok", true).toString());
        registry.defineBuiltinAction("nil", buildMetaActionInfo("nil"), params -> null);

        Assertions.assertEquals("hello", registry.call("builtin::echo", Map.of("value", "hello")));
        Assertions.assertEquals("{ok=true}", registry.call("builtin::json", Map.of()));
        Assertions.assertEquals("null", registry.call("builtin::nil", Map.of()));
    }

    @Test
    void testCallThrowsWhenMissingDefinition() {
        BuiltinActionRegistry registry = new BuiltinActionRegistry();
        Assertions.assertThrows(ActionLookupException.class, () -> registry.call("builtin::missing", Map.of()));
    }

    @Test
    void testCallPropagatesInvokerFailure() {
        BuiltinActionRegistry registry = new BuiltinActionRegistry();
        registry.defineBuiltinAction("boom", buildMetaActionInfo("boom"), params -> {
            throw new IllegalStateException("boom");
        });

        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
                () -> registry.call("builtin::boom", Map.of()));
        Assertions.assertEquals("boom", exception.getMessage());
    }

}
