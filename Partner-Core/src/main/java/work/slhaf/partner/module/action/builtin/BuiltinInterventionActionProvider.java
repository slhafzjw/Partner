package work.slhaf.partner.module.action.builtin;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.entity.Action;
import work.slhaf.partner.core.action.entity.ExecutableAction;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.entity.intervention.InterventionType;
import work.slhaf.partner.core.action.entity.intervention.MetaIntervention;
import work.slhaf.partner.core.cognition.*;
import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;
import work.slhaf.partner.framework.agent.exception.ExceptionReporterHandler;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.annotation.AgentComponent;
import work.slhaf.partner.framework.agent.support.Result;

import java.util.*;
import java.util.function.Function;

import static work.slhaf.partner.core.action.ActionCore.BUILTIN_LOCATION;

@AgentComponent
class BuiltinInterventionActionProvider implements BuiltinActionProvider {

    private static final String INTERVENTION_LOCATION = BUILTIN_LOCATION + "::" + "intervention";

    private final Set<String> basicTags = Set.of("Builtin MetaAction", "Action Intervention");

    @InjectCapability
    private ActionCapability actionCapability;
    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    public List<BuiltinActionRegistry.BuiltinActionDefinition> provideBuiltinActions() {
        return List.of(
                buildCreateInterventionDefinition(),
                buildShowAvailableMetaActionsDefinition(),
                buildShowIntervenableActionsDefinition(),
                buildAcquireInterventionDefinition(),
                buildResumeInterruptedActionDefinition()
        );
    }

    private BuiltinActionRegistry.BuiltinActionDefinition buildResumeInterruptedActionDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Agent Turn");
        tags.add("Action Management");

        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of(
                        "actionId", param("required", "string", "Uuid of an executable action currently in INTERRUPTED status.")
                ),
                "Purpose: resume an executable action that is currently interrupted. Inputs: actionId. Returns: plain text success or failure reason. Use when: an intervention or user decision is complete and the original action should continue. Notes: this does not modify the action chain; use create_intervention before resuming when chain changes are required.",
                tags,
                Set.of(),
                Set.of(),
                false,
                JSONObject.of(
                        "result", "Plain text resume result, describes whether it is succeed or fail reason."
                )
        );

        Function<Map<String, Object>, String> invoker = params -> {
            String actionId = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "actionId");
            try {
                ExecutableAction executableAction = getExecutableAction(actionId);
                executableAction.resume();
                return "Resume succeed";
            } catch (Exception e) {
                return "Failed to resume action[" + actionId + "], reason: " + e.getLocalizedMessage();
            }
        };

        return new BuiltinActionRegistry.BuiltinActionDefinition(
                createActionKey("resume_interrupted_action"),
                info,
                invoker
        );
    }


    /**
     * 尝试向指定用户请求干预，通过自对话通道
     *
     * @return 内建 MetaAction 定义数据
     */
    private BuiltinActionRegistry.BuiltinActionDefinition buildAcquireInterventionDefinition() {
        Set<String> tags = new HashSet<>(basicTags);
        tags.add("Agent Turn");
        tags.add("Action Management");

        MetaActionInfo info = new MetaActionInfo(
                true,
                null,
                Map.of(
                        "actionId", param("required", "string", "Uuid of the executing action that should be interrupted for external intervention."),
                        "actionInfo", param("required", "string", "Readable summary of the current action and why intervention is needed."),
                        "demand", param("required", "string", "Specific feedback, decision, permission, or operation required from the target."),
                        "target", param("required", "string", "Target user or channel identifier that should receive the intervention request."),
                        "input", param("required", "string", "Prompt content used to initiate the intervention turn toward the target."),
                        "timeout", param("required", "int", "Maximum wait time passed to ExecutableAction.interrupt(timeout).")
                ),
                "Purpose: interrupt an executing action and ask a target participant/channel for intervention. Inputs: actionId, actionInfo, demand, target, input, timeout. Returns: plain text intervention status. Use when: execution cannot safely continue without user decision, missing information, permission, or manual operation. Notes: this creates communication side effects and temporarily interrupts the action.",
                tags,
                Set.of(),
                Set.of(),
                false,
                JSONObject.of(
                        "result", "Plain text intervention status. It describes whether the target answered before timeout, or returns an error reason when the turn/interruption flow fails."
                )
        );

        Function<Map<String, Object>, String> invoker = params -> {
            String actionId = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "actionId").trim();
            String actionInfo = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "actionInfo").trim();
            String demand = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "demand").trim();
            String target = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "target").trim();
            String input = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "input").trim();
            int timeout = BuiltinActionRegistry.BuiltinActionDefinition.requireInt(params, "timeout");

            ContextWorkspace contextWorkspace = cognitionCapability.contextWorkspace();
            String blockName = "acquire_intervention-" + actionId;
            String source = "action_executor";
            contextWorkspace.register(new ContextBlock(
                    new CommunicationBlockContent(blockName, source, BlockContent.Urgency.HIGH, CommunicationBlockContent.Projection.SUPPLY) {
                        @Override
                        protected void fillXml(@NotNull Document document, @NotNull Element root) {
                            appendTextElement(document, root, "state", "Partner needs some help.");
                            appendTextElement(document, root, "action_id", actionId);
                            appendTextElement(document, root, "action_info", actionInfo);
                            appendTextElement(document, root, "demand", demand);
                        }
                    },
                    Set.of(ContextBlock.FocusedDomain.COMMUNICATION),
                    10,
                    10,
                    20
            ));

            ExecutableAction executableAction = null;
            try {
                executableAction = getExecutableAction(actionId);
                cognitionCapability.initiateTurn(input, target);
                boolean normal = executableAction.interrupt(timeout);
                return normal ? target + "not resumed execution in time" : target + "answered, looking for related answer in recent-chat-messages";
            } catch (Exception e) {
                return "Error happened while calling turn: " + e.getLocalizedMessage();
            } finally {
                contextWorkspace.expire(blockName, source);
                if (executableAction != null) {
                    executableAction.resume();
                }
            }
        };

        return new BuiltinActionRegistry.BuiltinActionDefinition(
                createActionKey("acquire_intervention"),
                info,
                invoker
        );
    }

    private ExecutableAction getExecutableAction(String actionId) {
        return actionCapability.listActions(Action.Status.EXECUTING, null)
                .stream()
                .filter(action -> action.getUuid().equals(actionId))
                .findFirst()
                .orElseThrow();
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
                "Purpose: list executable actions that can be inspected for possible intervention. Inputs: none. Returns: JSON array with tendency, description, status, and uuid for each action. Use when: an action id is needed before acquire_intervention, create_intervention, or resume_interrupted_action. Notes: this only discovers actions; it does not modify them.",
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
                "Purpose: list currently available MetaActions that can be used in action chains or interventions. Inputs: none. Returns: JSON array with action key/location, description, tags, and params. Use when: correction needs to discover valid action_key values for APPEND, INSERT, DELETE, or REBUILD. Notes: this is a discovery action and does not execute those MetaActions.",
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
                        "id", param("required", "string", "Uuid of the executable action to modify."),
                        "type", param("required", "string", "Intervention type. Allowed values: APPEND, INSERT, DELETE, CANCEL, REBUILD."),
                        "order", param("required", "int", "Action chain order/stage where the intervention applies."),
                        "actions", param("conditional", "string", "Comma-separated actionKey list to insert, append, rebuild, or delete. Required except for CANCEL. Example: builtin::command::execute,builtin::command::read.")
                ),
                "Purpose: apply a structural intervention to an executable action chain. Inputs: id, type, order, and conditional actions. Returns: JSON with ok and optional result. Use when: correction must append, insert, delete, cancel, or rebuild future action-chain steps. Notes: actions must be valid action_key values; CANCEL may omit actions; this has direct action-chain side effects and resumes the target action after applying changes.",
                basicTags,
                Set.of(
                        createActionKey("list_available_meta_actions"),
                        createActionKey("list_intervenable_actions")
                ),
                Set.of(),
                true,
                JSONObject.of(
                        "ok", "Intervene status",
                        "result", "Intervene result or failed message."
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
            Result<Void> validationResult = validateActionKeys(actions);
            AgentRuntimeException validationFailure = validationResult.onFailure(ExceptionReporterHandler.INSTANCE::report).exceptionOrNull();
            if (validationFailure != null) {
                return JSONObject.of("ok", false, "result", validationFailure.getLocalizedMessage()).toJSONString();
            }

            MetaIntervention intervention = new MetaIntervention();
            intervention.setType(type);
            intervention.setOrder(order);
            intervention.setActions(actions);

            actionCapability.handleInterventions(List.of(intervention), target);

            ExecutableAction executableAction = getExecutableAction(targetId);
            executableAction.resume();

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

    private Result<Void> validateActionKeys(List<String> actions) {
        for (String actionKey : actions) {
            Result<MetaAction> metaActionResult = actionCapability.loadMetaAction(actionKey);
            AgentRuntimeException failure = metaActionResult.exceptionOrNull();
            if (failure != null) {
                return Result.failure(failure);
            }
        }
        return Result.success(null);
    }

}
