package work.slhaf.partner.module.modules.action.builtin;

import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;
import lombok.NonNull;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.annotation.Init;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.exception.MetaActionNotFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static work.slhaf.partner.core.action.ActionCore.BUILTIN_LOCATION;

public class BuiltinActionRegistry extends AbstractAgentModule.Standalone {

    @Getter
    private final Map<String, BuiltinActionDefinition> definitions = new LinkedHashMap<>();
    @InjectCapability
    private ActionCapability actionCapability;

    public static BuiltinActionDefinition definition(String name, MetaActionInfo metaActionInfo,
                                                     Function<Map<String, Object>, Object> invoker) {
        return new BuiltinActionDefinition(BUILTIN_LOCATION + "::" + name, metaActionInfo, invoker);
    }

    @Init
    public void init() {
        definitions.clear();
        for (BuiltinActionDefinition definition : buildDefinitions()) {
            definitions.put(definition.actionKey(), definition);
        }
        actionCapability.registerMetaActions(exportMetaActionInfos());
        actionCapability.runnerClient().setBuiltinActionRegistry(this);
    }

    protected List<BuiltinActionDefinition> buildDefinitions() {
        return List.of();
    }

    public String call(@NonNull String actionKey, @NonNull Map<String, Object> params) {
        BuiltinActionDefinition definition = definitions.get(actionKey);
        if (definition == null) {
            throw new MetaActionNotFoundException("未找到对应的内置行动程序: " + actionKey);
        }
        Object result = definition.invoker().apply(params);
        if (result == null) {
            return "null";
        }
        if (result instanceof String string) {
            return string;
        }
        if (result instanceof Number || result instanceof Boolean || result instanceof Map || result instanceof Iterable) {
            return JSONObject.toJSONString(result);
        }
        return String.valueOf(result);
    }

    private Map<String, MetaActionInfo> exportMetaActionInfos() {
        Map<String, MetaActionInfo> metaActions = new LinkedHashMap<>();
        definitions.forEach((key, value) -> metaActions.put(key, value.metaActionInfo()));
        return metaActions;
    }

    public record BuiltinActionDefinition(
            String actionKey,
            MetaActionInfo metaActionInfo,
            Function<Map<String, Object>, Object> invoker
    ) {
    }
}
