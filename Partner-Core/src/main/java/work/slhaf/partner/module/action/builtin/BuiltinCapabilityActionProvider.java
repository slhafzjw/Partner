package work.slhaf.partner.module.action.builtin;

import com.alibaba.fastjson2.JSONObject;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.annotation.AgentComponent;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.cognition.CognitionCapability;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static work.slhaf.partner.core.action.ActionCore.BUILTIN_LOCATION;

@AgentComponent
class BuiltinCapabilityActionProvider implements BuiltinActionProvider {

    private static final String CAPABILITY_LOCATION = BUILTIN_LOCATION + "::" + "capability";

    private final Set<String> basicTags = Set.of("Builtin MetaAction", "Agent Capability");

    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    public List<BuiltinActionRegistry.BuiltinActionDefinition> provideBuiltinActions() {
        return List.of(
                buildInitiateTurnDefinition()
        );
    }


    /**
     * 用于发起自对话的 Builtin MetaAction
     *
     * @return 内建 MetaAction 定义数据
     */
    private BuiltinActionRegistry.BuiltinActionDefinition buildInitiateTurnDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Agent Turn");

        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of(
                        "input", "Input required to initiate an internal Agent Turn.",
                        "target", "The people expected to reply to this internal Agent Turn."
                ),
                "Create an internal Agent Turn to resolve a task.",
                tags,
                Set.of(),
                Set.of(),
                false,
                JSONObject.of(
                        "answer", "The answer of the Agent Turn."
                )
        );

        Function<Map<String, Object>, String> invoker = params -> {
            String input = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "input");
            String target = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "target");
            return cognitionCapability.initiateTurn(input, target);
        };

        return new BuiltinActionRegistry.BuiltinActionDefinition(
                createActionKey("initiate_turn"),
                info,
                invoker
        );
    }

    @Override
    public String createActionKey(String actionName) {
        return CAPABILITY_LOCATION + "::" + actionName;
    }
}
