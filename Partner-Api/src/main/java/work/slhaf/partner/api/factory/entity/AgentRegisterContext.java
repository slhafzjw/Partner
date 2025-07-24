package work.slhaf.partner.api.factory.entity;

import lombok.Data;
import org.reflections.Reflections;

import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;

@Data
public class AgentRegisterContext {
    private Reflections reflections;
    private final HashMap<String, Function<Object[], Object>> methodsRouterTable = new HashMap<>();
    private final HashMap<String, Function<Object[], Object>> coordinatedMethodsRouterTable = new HashMap<>();
    private final HashMap<Class<?>, Object> capabilityCoreInstances = new HashMap<>();
    private final HashMap<Class<?>, Object> capabilityHolderInstances = new HashMap<>();
    private Set<Class<?>> cores;
    private Set<Class<?>> capabilities;

    public AgentRegisterContext(String path) {
        reflections = new Reflections(path);
    }
}
