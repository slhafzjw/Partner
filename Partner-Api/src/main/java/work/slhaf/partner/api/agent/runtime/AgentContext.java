package work.slhaf.partner.api.agent.runtime;

import lombok.Data;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;

import java.util.HashMap;
import java.util.List;
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
    private List<MetaModule> moduleList;
}
