package work.slhaf.partner.module.action.builtin;

import lombok.Getter;
import lombok.NonNull;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.exception.ActionLookupException;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;

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
        BuiltinActionProvider capabilityActionProvider = new BuiltinCapabilityActionProvider();
        BuiltinActionProvider interventionActionProvider = new BuiltinInterventionActionProvider();
        BuiltinActionProvider dynamicActionProvider = new BuiltinDynamicActionProvider();

        builtinActionDefinitions.addAll(commandActionProvider.provideBuiltinActions());
        builtinActionDefinitions.addAll(capabilityActionProvider.provideBuiltinActions());
        builtinActionDefinitions.addAll(interventionActionProvider.provideBuiltinActions());
        builtinActionDefinitions.addAll(dynamicActionProvider.provideBuiltinActions());

        return builtinActionDefinitions;
    }

    public void defineBuiltinAction(String name, MetaActionInfo metaActionInfo, Function<Map<String, Object>, String> invoker) {
        BuiltinActionDefinition definition = new BuiltinActionDefinition(BUILTIN_LOCATION + "::" + name, metaActionInfo, invoker);
        definitions.put(definition.actionKey(), definition);
    }

    public String call(@NonNull String actionKey, @NonNull Map<String, Object> params) {
        BuiltinActionDefinition definition = definitions.get(actionKey);
        if (definition == null) {
            throw new ActionLookupException(
                    "Builtin action definition not found: " + actionKey,
                    actionKey,
                    "BUILTIN_DEFINITION"
            );
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

        static Integer requireInt(Map<String, Object> params, String key) {
            Object value = params.get(key);
            if (value == null) {
                throw new IllegalArgumentException("缺少参数: " + key);
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            throw new IllegalArgumentException("参数 " + key + " 必须为整数");
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
