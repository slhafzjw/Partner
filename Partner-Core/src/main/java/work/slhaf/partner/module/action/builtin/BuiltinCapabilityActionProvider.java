package work.slhaf.partner.module.action.builtin;

import com.alibaba.fastjson2.JSONObject;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.cognition.BlockContent;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.memory.pojo.MemorySlice;
import work.slhaf.partner.core.memory.pojo.MemoryUnit;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.annotation.AgentComponent;
import work.slhaf.partner.framework.agent.support.Result;

import java.util.*;
import java.util.function.Function;

import static work.slhaf.partner.core.action.ActionCore.BUILTIN_LOCATION;

@AgentComponent
class BuiltinCapabilityActionProvider implements BuiltinActionProvider {

    private static final String CAPABILITY_LOCATION = BUILTIN_LOCATION + "::" + "capability";

    private final Set<String> basicTags = Set.of("Builtin MetaAction", "Agent Capability");

    @InjectCapability
    private CognitionCapability cognitionCapability;
    @InjectCapability
    private MemoryCapability memoryCapability;

    @Override
    public List<BuiltinActionRegistry.BuiltinActionDefinition> provideBuiltinActions() {
        return List.of(
                buildInitiateTurnDefinition(),
                buildMemoryRecallDefinition()
        );
    }

    private BuiltinActionRegistry.BuiltinActionDefinition buildMemoryRecallDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Memory");

        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of(
                        "unit_id", param("required", "string", "Memory unit id that contains the target memory slice."),
                        "slice_id", param("required", "string", "Memory slice id to load into short-lived context.")
                ),
                "Purpose: bring a known memory slice back into the agent's working context so later reasoning can use the original conversation messages. Use when: the exact memory unit and slice have already been identified. Input: memory slice id and memory unit id. Does not: search, rank, summarize, or infer memories. Returns: whether the slice was recalled and a short result message.",
                tags,
                Set.of(),
                Set.of(),
                false,
                JSONObject.of(
                        "ok", "boolean, whether the target memory slice is found and recalled successfully",
                        "message", "string, short execution result description"
                )
        );

        Function<Map<String, Object>, String> invoker = params -> {
            String unitId = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "unit_id");
            String sliceId = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "slice_id");
            Result<MemorySlice> sliceResult = memoryCapability.getMemorySlice(unitId, sliceId);
            if (sliceResult.exceptionOrNull() != null) {
                return JSONObject.of(
                        "ok", false,
                        "message", sliceResult.exceptionOrNull().getLocalizedMessage()
                ).toJSONString();
            }
            MemorySlice slice = sliceResult.getOrThrow();

            MemoryUnit unit = memoryCapability.getMemoryUnit(unitId);
            cognitionCapability.contextWorkspace().register(new ContextBlock(
                    buildMemoryRecallFullBlock(unit, slice),
                    Set.of(ContextBlock.FocusedDomain.MEMORY),
                    60,
                    16,
                    28
            ));

            return JSONObject.of(
                    "ok", true,
                    "message", "Memory slice found and recalled into context"
            ).toJSONString();
        };
        return new BuiltinActionRegistry.BuiltinActionDefinition(
                createActionKey("memory_recall"),
                info,
                invoker
        );
    }

    private @NotNull BlockContent buildMemoryRecallFullBlock(MemoryUnit unit, MemorySlice slice) {
        return new BlockContent("memory_recall", "memory_capability") {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                root.setAttribute("unit_id", unit.getId());
                root.setAttribute("slice_id", slice.getId());
                appendRepeatedElements(document, root, "message", unit.getConversationMessages().subList(slice.getStartIndex(), slice.getEndIndex()), (messageElement, message) -> {
                    messageElement.setAttribute("role", message.getRole().name().toLowerCase(Locale.ROOT));
                    messageElement.setTextContent(message.getContent());
                    return Unit.INSTANCE;
                });
            }
        };
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
                        "input", param("required", "string", "Prompt content for the internal Agent Turn."),
                        "target", param("required", "string", "Target user expected to receive/respond to the turn.")
                ),
                "Purpose: initiate a self-dialogue or proactive dialogue by sending input to a target participant. Use when: the agent needs to start a new turn for reflection, clarification, coordination, or active communication. Input: input content and the user expected to respond. Returns: confirmation that the turn was initiated. Does not: wait for or include the target's answer.",
                tags,
                Set.of(),
                Set.of(),
                false,
                JSONObject.of(
                        "result", "turn initiate result"
                )
        );

        Function<Map<String, Object>, String> invoker = params -> {
            String input = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "input");
            String target = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "target");
            cognitionCapability.initiateTurn(input, target);
            return "agent turn initiated";
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
