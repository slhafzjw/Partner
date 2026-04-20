package work.slhaf.partner.module.action.planner;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.framework.agent.support.Result;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ActionPlannerAssemblyHelperTest {

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    @Test
    void shouldFixNonZeroBasedOrdersWithoutInjectingNullActionKeyList() throws Exception {
        ActionCapability actionCapability = Mockito.mock(ActionCapability.class);
        Mockito.when(actionCapability.loadMetaActionInfo(Mockito.anyString()))
                .thenReturn(Result.success(new MetaActionInfo(
                        false,
                        null,
                        Map.of(),
                        "desc",
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        false,
                        new JSONObject()
                )));

        ActionPlanner planner = new ActionPlanner();
        injectField(planner, "actionCapability", actionCapability);

        Object helper = getField(planner, "assemblyHelper");
        Method fixDependencies = helper.getClass().getDeclaredMethod("fixDependencies", Map.class);
        fixDependencies.setAccessible(true);

        Map<Integer, List<String>> chain = new LinkedHashMap<>();
        chain.put(1, new ArrayList<>(List.of("action_a")));
        chain.put(2, new ArrayList<>(List.of("action_b")));

        boolean fixed = (boolean) fixDependencies.invoke(helper, chain);

        assertTrue(fixed);
        assertEquals(List.of(1, 2), new ArrayList<>(chain.keySet()));
        assertEquals(List.of("action_a"), chain.get(1));
        assertEquals(List.of("action_b"), chain.get(2));
        assertFalse(chain.containsValue(null));
    }
}
