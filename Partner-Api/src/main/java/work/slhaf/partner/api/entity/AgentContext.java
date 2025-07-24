package work.slhaf.partner.api.entity;

import lombok.Data;

import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;

@Data
public class AgentContext {
    private HashMap<String, Function<Object[], Object>> methodsRouterTable;
    private HashMap<String, Function<Object[], Object>> coordinatedMethodsRouterTable;
    private HashMap<Class<?>, Object> capabilityCoreInstances;
    private HashMap<Class<?>, Object> capabilityHolderInstances;
    private Set<Class<?>> cores;
    private Set<Class<?>> capabilities;
}
