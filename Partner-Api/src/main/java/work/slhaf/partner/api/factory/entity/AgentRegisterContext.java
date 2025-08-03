package work.slhaf.partner.api.factory.entity;

import lombok.Data;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import work.slhaf.partner.api.common.chat.pojo.Message;
import work.slhaf.partner.api.factory.config.pojo.ModelConfig;
import work.slhaf.partner.api.factory.module.pojo.MetaModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private HashMap<String, List<Message>> modelPromptMap = new HashMap<>();
    private HashMap<String, ModelConfig> modelConfigMap = new HashMap<>();
    private List<MetaModule> moduleList = new ArrayList<>();

    public AgentRegisterContext(List<String> paths) {
        reflections = new Reflections(new ConfigurationBuilder().setScanners(
                        Scanners.FieldsAnnotated,
                        Scanners.SubTypes,
                        Scanners.MethodsAnnotated,
                        Scanners.TypesAnnotated
                )
                .forPackages(paths.toArray(paths.toArray(new String[0])))
        );
    }
}
