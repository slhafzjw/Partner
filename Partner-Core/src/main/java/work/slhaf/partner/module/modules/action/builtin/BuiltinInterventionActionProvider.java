package work.slhaf.partner.module.modules.action.builtin;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.annotation.AgentComponent;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.entity.ExecutableAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.module.modules.action.interventor.entity.InterventionType;
import work.slhaf.partner.module.modules.action.interventor.entity.MetaIntervention;

import java.util.*;
import java.util.function.Function;

import static work.slhaf.partner.core.action.ActionCore.BUILTIN_LOCATION;

@AgentComponent
class BuiltinInterventionActionProvider implements BuiltinActionProvider {

    private static final String INTERVENTION_LOCATION = BUILTIN_LOCATION + "::" + "intervention";

    private final Set<String> basicTags = Set.of("Builtin MetaAction", "Action Intervention");

    @InjectCapability
    private ActionCapability actionCapability;

    @Override
    public List<BuiltinActionRegistry.BuiltinActionDefinition> provideBuiltinActions() {
        return List.of(
                buildCreateInterventionDefinition(),
                buildShowAvailableMetaActionsDefinition(),
                buildShowIntervenableActionsDefinition()
        );
    }

    /**
     * 用于展示当前已存在的可被干预的行动
     *
     * @return 内建 MetaAction 定义数据
     */
    private BuiltinActionRegistry.BuiltinActionDefinition buildShowIntervenableActionsDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Available Resource");
        tags.add("Agent State");

        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of(),
                "List existing actions that can be intervened",
                tags,
                Set.of(),
                Set.of(),
                false,
                JSONObject.of(
                        "actions", "Info list of actions that could be intervened.",
                        "action.tendency", "Original tendency that the action is intended to resolve.",
                        "action.description", "Description of this action.",
                        "action.status", "Execution status of this action.",
                        "action.uuid", "Unique uuid of each action."
                )
        );

        Function<Map<String, Object>, String> invoker = params -> {
            Set<ExecutableAction> executableActions = actionCapability.listActions(null, null);
            JSONArray interventions = new JSONArray();
            for (ExecutableAction action : executableActions) {
                JSONObject item = interventions.addObject();
                item.put("tendency", action.getTendency());
                item.put("description", action.getDescription());
                item.put("status", action.getStatus().name().toLowerCase());
                item.put("uuid", action.getUuid());
            }
            return interventions.toJSONString();
        };

        return new BuiltinActionRegistry.BuiltinActionDefinition(
                createActionKey("list_intervenable_actions"),
                info,
                invoker
        );
    }

    /**
     * 用于展示当前可用的 MetaAction
     *
     * @return 内建 MetaAction 定义数据
     */
    private BuiltinActionRegistry.BuiltinActionDefinition buildShowAvailableMetaActionsDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Available Resource");

        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of(),
                "List available MetaActions.",
                tags,
                Set.of(),
                Set.of(),
                false,
                JSONObject.of(
                        "meta_actions", "Available MetaAction info list.",
                        "meta_action.actionKey", "MetaAction actionKey, describing action source and its unique calling id.",
                        "meta_action.description", "MetaAction description.",
                        "meta_action.tags", "MetaAction tag list as string.",
                        "meta_action.params", "MetaAction parameter definition JSON string."
                )
        );

        Function<Map<String, Object>, String> invoker = params -> {
            Map<String, MetaActionInfo> availableMetaActions = actionCapability.listAvailableMetaActions();
            JSONArray actions = new JSONArray();
            for (Map.Entry<String, MetaActionInfo> entry : availableMetaActions.entrySet()) {
                JSONObject item = actions.addObject();
                item.put("location", entry.getKey());

                MetaActionInfo action = entry.getValue();
                item.put("description", action.getDescription());
                item.put("tags", Arrays.toString(action.getTags().toArray()));
                item.put("params", JSONObject.toJSONString(action.getParams()));
            }
            return actions.toJSONString();
        };
        return new BuiltinActionRegistry.BuiltinActionDefinition(
                createActionKey("list_available_meta_actions"),
                info,
                invoker
        );
    }

    /**
     * 用于创建一个 Action Intervention，并作用于指定的 Executable Action
     *
     * @return 内建 MetaAction 定义数据
     */
    private BuiltinActionRegistry.BuiltinActionDefinition buildCreateInterventionDefinition() {
        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of(
                        "id", "The uuid of the Action to be intervened on.",
                        "type", "Intervention type. Allowed values: APPEND, INSERT, REBUILD, DELETE, CANCEL.",
                        "order", "Action chain order/stage to apply the intervention on.",
                        "actions", "Comma-separated actionKey list to be inserted, appended, rebuilt or deleted. Example: \"builtin::command::execute, builtin::capability::show_memory_slices\""
                ),
                "Used to create an Action Intervention and act on the specified Executable Action.",
                basicTags,
                Set.of(
                        createActionKey("list_available_meta_actions"),
                        createActionKey("list_intervenable_actions")
                ),
                Set.of(),
                true,
                JSONObject.of(
                        "result", "Intervene status."
                )
        );

        Function<Map<String, Object>, String> invoker = params -> {
            String targetId = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "id").trim();
            if (targetId.isEmpty()) {
                throw new IllegalArgumentException("参数 id 不能为空");
            }

            InterventionType type = requireInterventionType(params);
            Integer order = BuiltinActionRegistry.BuiltinActionDefinition.requireInt(params, "order");
            List<String> actions = requireActions(params, type);
            ExecutableAction target = requireTargetAction(targetId);

            MetaIntervention intervention = new MetaIntervention();
            intervention.setType(type);
            intervention.setOrder(order);
            intervention.setActions(actions);

            actionCapability.handleInterventions(List.of(intervention), target);
            return JSONObject.of("ok", true).toJSONString();
        };

        return new BuiltinActionRegistry.BuiltinActionDefinition(
                createActionKey("create_intervention"),
                info,
                invoker
        );
    }

    @Override
    public String createActionKey(String actionName) {
        return INTERVENTION_LOCATION + "::" + actionName;
    }

    private InterventionType requireInterventionType(Map<String, Object> params) {
        String typeValue = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "type").trim();
        if (typeValue.isEmpty()) {
            throw new IllegalArgumentException("参数 type 不能为空");
        }
        try {
            return InterventionType.valueOf(typeValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("参数 type 非法: " + typeValue, e);
        }
    }

    private List<String> requireActions(Map<String, Object> params, InterventionType type) {
        Object value = params.get("actions");
        if (value == null) {
            if (type == InterventionType.CANCEL) {
                return List.of();
            }
            throw new IllegalArgumentException("缺少参数: actions");
        }

        if (!(value instanceof String actionsValue)) {
            throw new IllegalArgumentException("参数 actions 必须为字符串");
        }

        String trimmedActionsValue = actionsValue.trim();
        if (trimmedActionsValue.isEmpty()) {
            if (type == InterventionType.CANCEL) {
                return List.of();
            }
            throw new IllegalArgumentException("参数 actions 不能为空");
        }

        List<String> actions = Arrays.stream(trimmedActionsValue.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();

        if (type != InterventionType.CANCEL && actions.isEmpty()) {
            throw new IllegalArgumentException("参数 actions 不能为空");
        }
        return actions;
    }

    private ExecutableAction requireTargetAction(String targetId) {
        return actionCapability.listActions(null, null)
                .stream()
                .filter(action -> targetId.equals(action.getUuid()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到对应的 Action: " + targetId));
    }

}
