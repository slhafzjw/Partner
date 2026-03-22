package work.slhaf.partner.module.modules.action.builtin;

import com.alibaba.fastjson2.JSONObject;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.annotation.AgentComponent;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.perceive.PerceiveCapability;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private MemoryCapability memoryCapability;
    @InjectCapability
    private CognitionCapability cognitionCapability;
    @InjectCapability
    private PerceiveCapability perceiveCapability;

    @Override
    public List<BuiltinActionRegistry.BuiltinActionDefinition> provideBuiltinActions() {
        return List.of(
                buildShowActivatedSlicesDefinition(),
                buildInteractionStatusDefinition(),
                buildInitiateTurnDefinition()
        );
    }

    /**
     * 用于展示当前已激活的记忆切片的 Builtin MetaAction
     *
     * @return 内建 MetaAction 定义数据
     */
    private BuiltinActionRegistry.BuiltinActionDefinition buildShowActivatedSlicesDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Memory");

        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of(),
                "Show memory slices content that is activated.",
                tags,
                Set.of(),
                Set.of(),
                false,
                JSONObject.of(
                        "memory_slices", "Array of activated memory slices.",
                        "memory_slice.unitId", "Id of the MemoryUnit which this MemorySlice belongs to.",
                        "memory_slice.summary", "Summary of the MemorySlice.",
                        "memory_slice.date", "Date of the MemorySlice that is created."
                )
        );

        Function<Map<String, Object>, String> invoker = params -> {
            List<JSONObject> memorySlices = memoryCapability.getActivatedSlices().stream()
                    .map(activatedSlice -> JSONObject.of(
                            "unitId", activatedSlice.getUnitId(),
                            "summary", activatedSlice.getSummary(),
                            "date", activatedSlice.getDate().toString()
                    ))
                    .toList();
            return JSONObject.of("memory_slices", memorySlices).toJSONString();
        };

        return new BuiltinActionRegistry.BuiltinActionDefinition(
                createActionKey("list_activated_memory"),
                info,
                invoker
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

    /**
     * 用于获取当前交互状态的 Builtin MetaAction
     *
     * @return 内建 MetaAction 定义数据
     */
    private BuiltinActionRegistry.BuiltinActionDefinition buildInteractionStatusDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Agent Status");

        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of(),
                "View last interaction time.",
                tags,
                Set.of(),
                Set.of(),
                false,
                JSONObject.of(
                        "lastInteractTime", "Last interaction time."
                )
        );

        Function<Map<String, Object>, String> invoker = params -> perceiveCapability.showLastInteract().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

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
