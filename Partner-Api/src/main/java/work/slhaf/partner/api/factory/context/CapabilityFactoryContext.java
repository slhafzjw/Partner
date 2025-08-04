package work.slhaf.partner.api.factory.context;

import lombok.Data;

import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;

@Data
public class CapabilityFactoryContext {
    private final HashMap<String, Function<Object[], Object>> methodsRouterTable = new HashMap<>();
    private final HashMap<String, Function<Object[], Object>> coordinatedMethodsRouterTable = new HashMap<>();
    private final HashMap<Class<?>, Object> capabilityCoreInstances = new HashMap<>();
    private final HashMap<Class<?>, Object> capabilityHolderInstances = new HashMap<>();
    private Set<Class<?>> cores;
    private Set<Class<?>> capabilities;
}
