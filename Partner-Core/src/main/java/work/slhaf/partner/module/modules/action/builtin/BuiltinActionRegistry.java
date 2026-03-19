package work.slhaf.partner.module.modules.action.builtin;

import lombok.Getter;
import lombok.NonNull;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.annotation.Init;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.exception.MetaActionNotFoundException;

import java.util.ArrayList;
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

    @Init
    public void init() {
        definitions.clear();
        for (BuiltinActionDefinition definition : buildDefaultActionDefinitions()) {
            definitions.put(definition.actionKey(), definition);
        }
        actionCapability.registerMetaActions(exportMetaActionInfos());
        actionCapability.runnerClient().setBuiltinActionRegistry(this);
    }

    protected List<BuiltinActionDefinition> buildDefaultActionDefinitions() {
        List<BuiltinActionDefinition> builtinActionDefinitions = new ArrayList<>();
        BuiltinActionProvider commandActionProvider = new BuiltinCommandActionProvider();
        builtinActionDefinitions.addAll(commandActionProvider.provideBuiltinActions());
        return builtinActionDefinitions;
    }

    public void defineBuiltinAction(String name, MetaActionInfo metaActionInfo, Function<Map<String, Object>, String> invoker) {
        BuiltinActionDefinition definition = new BuiltinActionDefinition(BUILTIN_LOCATION + "::" + name, metaActionInfo, invoker);
        definitions.put(definition.actionKey(), definition);
    }

    public String call(@NonNull String actionKey, @NonNull Map<String, Object> params) {
        BuiltinActionDefinition definition = definitions.get(actionKey);
        if (definition == null) {
            throw new MetaActionNotFoundException("未找到对应的内置行动程序: " + actionKey);
        }
        String result = definition.invoker().apply(params);
        if (result == null) {
            return "null";
        }
        return result;
    }

    private Map<String, MetaActionInfo> exportMetaActionInfos() {
        Map<String, MetaActionInfo> metaActions = new LinkedHashMap<>();
        definitions.forEach((key, value) -> metaActions.put(key, value.metaActionInfo()));
        return metaActions;
    }

    public record BuiltinActionDefinition(
            String actionKey,
            MetaActionInfo metaActionInfo,
            Function<Map<String, Object>, String> invoker
    ) {

        static String requireString(Map<String, Object> params, String key) {
            Object value = params.get(key);
            if (value == null) {
                throw new IllegalArgumentException("缺少参数: " + key);
            }
            if (!(value instanceof String s)) {
                throw new IllegalArgumentException("参数 " + key + " 必须为字符串");
            }
            return s;
        }

        static String optionalString(Map<String, Object> params, String key, String defaultValue) {
            Object value = params.get(key);
            if (value == null) {
                return defaultValue;
            }
            if (!(value instanceof String s)) {
                throw new IllegalArgumentException("参数 " + key + " 必须为字符串");
            }
            return s;
        }

        static Integer optionalInt(Map<String, Object> params, String key, Integer defaultValue) {
            Object value = params.get(key);
            if (value == null) {
                return defaultValue;
            }
            if (value instanceof Number n) {
                return n.intValue();
            }
            throw new IllegalArgumentException("参数 " + key + " 必须为整数");
        }

    }
}
