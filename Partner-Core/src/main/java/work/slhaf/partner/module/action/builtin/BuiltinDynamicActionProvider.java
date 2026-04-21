package work.slhaf.partner.module.action.builtin;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import kotlin.Unit;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.entity.*;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.annotation.AgentComponent;
import work.slhaf.partner.framework.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.module.action.scheduler.ActionScheduler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static work.slhaf.partner.core.action.ActionCore.BUILTIN_LOCATION;

@AgentComponent
class BuiltinDynamicActionProvider implements BuiltinActionProvider {
    private static final String ORIGIN_LOCATION = "origin";
    private static final long TEMP_ACTION_TTL_MILLIS = 30 * 60 * 1000L;
    private static final String DYNAMIC_LOCATION = BUILTIN_LOCATION + "::" + "dynamic";
    private final Set<String> basicTags = Set.of("Builtin MetaAction", "Dynamic Generation");
    private final ConcurrentHashMap<String, TempDynamicActionRecord> tempDynamicActions = new ConcurrentHashMap<>();
    @InjectCapability
    private ActionCapability actionCapability;
    @InjectModule
    private ActionScheduler actionScheduler;

    @Override
    public List<BuiltinActionRegistry.BuiltinActionDefinition> provideBuiltinActions() {
        return List.of(
                buildGenerateDynamicActionDefinition(),
                buildPersistDynamicActionDefinition()
        );
    }

    private BuiltinActionRegistry.BuiltinActionDefinition buildGenerateDynamicActionDefinition() {
        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of(
                        "desc", param("required", "string", "Human-readable description for the generated temporary action. This becomes the new action's planning description."),
                        "code", param("required", "string", "Dynamic action source code content."),
                        "codeType", param("required", "string", "Source file extension without or with leading dot, for example py, sh, js, or .py."),
                        "launcher", param("required", "string", "Interpreter or launcher command used to run the generated code, for example python3, bash, or node."),
                        "meta", param("required", "json-string", "MetaActionInfo fields for the generated action. Supported fields: io, params, tags, preActions, postActions, strictDependencies, responseSchema.")
                ),
                "Purpose: generate and register a temporary script as an ORIGIN MetaAction from supplied source code. Inputs: desc, code, codeType, launcher, and meta info. Returns: JSON with ok and temporary actionKey. Use when: existing MetaActions cannot perform the task and a temporary code-backed action is needed. Notes: this registers executable code with cleanup TTL; the generated action is temporary until builtin::dynamic::persist is called.",
                basicTags,
                Set.of(),
                Set.of(createActionKey("persist")),
                false,
                JSONObject.of(
                        "ok", "Whether the dynamic action was generated successfully.",
                        "actionKey", "Temporary ORIGIN actionKey."
                )
        );
        return new BuiltinActionRegistry.BuiltinActionDefinition(createActionKey("generate"), info, params -> {
            String desc = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "desc").trim();
            if (desc.isEmpty()) {
                throw new IllegalArgumentException("参数 desc 不能为空");
            }
            String code = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "code");
            String codeType = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "codeType");
            String launcher = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "launcher");
            String metaJson = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "meta");

            JSONObject meta = parseMeta(metaJson);
            MetaActionInfo metaActionInfo = buildMetaActionInfo(meta, launcher, desc);

            String tempName = "dyn-" + shortUuid();
            String location = actionCapability.runnerClient().buildTmpPath(tempName, codeType);
            MetaAction tempAction = new MetaAction(
                    tempName,
                    metaActionInfo.getIo(),
                    launcher,
                    MetaAction.Type.ORIGIN,
                    location
            );
            String actionKey = ORIGIN_LOCATION + "::" + location;
            try {
                actionCapability.runnerClient().tmpSerialize(tempAction, code, codeType);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("临时动态行动序列化失败", e);
            }
            actionCapability.registerMetaActions(Map.of(actionKey, metaActionInfo));

            ActionFileMetaData fileMetaData = buildActionFileMetaData(location, code, codeType);
            StateAction cleanupAction = buildCleanupAction(actionKey);
            tempDynamicActions.put(actionKey, new TempDynamicActionRecord(
                    actionKey,
                    location,
                    cleanupAction.getUuid(),
                    fileMetaData,
                    metaActionInfo
            ));
            actionScheduler.schedule(cleanupAction);
            return JSONObject.of(
                    "ok", true,
                    "actionKey", actionKey
            ).toJSONString();
        });
    }

    private BuiltinActionRegistry.BuiltinActionDefinition buildPersistDynamicActionDefinition() {
        MetaActionInfo info = new MetaActionInfo(
                false,
                null,
                Map.of("actionKey", param("required", "string", "Temporary ORIGIN actionKey returned by builtin::dynamic::generate.")),
                "Purpose: persist a previously generated temporary ORIGIN MetaAction and cancel its cleanup task. Inputs: actionKey from dynamic::generate. Returns: JSON with ok and actionKey. Use when: a generated temporary action should become reusable beyond its cleanup TTL. Notes: this has persistence side effects and should only be used when the generated action is validated or explicitly requested.",
                basicTags,
                Set.of(createActionKey("generate")),
                Set.of(),
                false,
                JSONObject.of(
                        "ok", "Whether the dynamic action was persisted successfully.",
                        "actionKey", "Temporary ORIGIN actionKey."
                )
        );
        return new BuiltinActionRegistry.BuiltinActionDefinition(createActionKey("persist"), info, params -> {
            String actionKey = BuiltinActionRegistry.BuiltinActionDefinition.requireString(params, "actionKey");
            TempDynamicActionRecord record = tempDynamicActions.get(actionKey);
            if (record == null) {
                throw new IllegalArgumentException("未找到对应临时动态行动: " + actionKey);
            }
            actionCapability.runnerClient().persistSerialize(record.metaActionInfo(), record.fileMetaData());
            actionScheduler.cancel(record.cleanupActionId());
            removeTempDynamicAction(actionKey);
            return JSONObject.of(
                    "ok", true,
                    "actionKey", actionKey
            ).toJSONString();
        });
    }

    @Override
    public String createActionKey(String actionName) {
        return DYNAMIC_LOCATION + "::" + actionName;
    }

    private JSONObject parseMeta(String metaJson) {
        try {
            return JSON.parseObject(metaJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("参数 meta 必须为合法 JSON 字符串", e);
        }
    }

    private MetaActionInfo buildMetaActionInfo(JSONObject meta, String launcher, String description) {
        return new MetaActionInfo(
                Boolean.TRUE.equals(meta.getBoolean("io")),
                launcher,
                copyStringMap(meta.getJSONObject("params")),
                description,
                toOrderedSet(meta.getJSONArray("tags")),
                toOrderedSet(meta.getJSONArray("preActions")),
                toOrderedSet(meta.getJSONArray("postActions")),
                Boolean.TRUE.equals(meta.getBoolean("strictDependencies")),
                copyJsonObject(meta.getJSONObject("responseSchema"))
        );
    }

    private ActionFileMetaData buildActionFileMetaData(String location, String code, String codeType) {
        ActionFileMetaData fileMetaData = new ActionFileMetaData();
        fileMetaData.setContent(code);
        fileMetaData.setExt(normalizeCodeType(codeType));
        fileMetaData.setName(extractFileBaseName(location, fileMetaData.getExt()));
        return fileMetaData;
    }

    private StateAction buildCleanupAction(String actionKey) {
        return new StateAction(
                "system",
                "dynamic-action-cleanup:" + actionKey,
                "清理临时动态行动",
                Schedulable.ScheduleType.ONCE,
                ZonedDateTime.now().plusSeconds(TEMP_ACTION_TTL_MILLIS / 1000).toString(),
                new StateAction.Trigger.Call(() -> {
                    removeTempDynamicAction(actionKey);
                    return Unit.INSTANCE;
                })
        );
    }

    private void removeTempDynamicAction(String actionKey) {
        TempDynamicActionRecord record = tempDynamicActions.remove(actionKey);
        if (record == null) {
            return;
        }
        actionCapability.listAvailableMetaActions().remove(actionKey);
        deleteTempFileQuietly(record.location());
    }

    private void deleteTempFileQuietly(String location) {
        try {
            Files.deleteIfExists(Path.of(location));
        } catch (Exception ignored) {
        }
    }

    private Map<String, String> copyStringMap(JSONObject jsonObject) {
        if (jsonObject == null) {
            return Map.of();
        }
        Map<String, String> params = new LinkedHashMap<>();
        jsonObject.forEach((key, value) -> params.put(key, value == null ? "" : String.valueOf(value)));
        return params;
    }

    private Set<String> toOrderedSet(com.alibaba.fastjson2.JSONArray jsonArray) {
        if (jsonArray == null) {
            return Set.of();
        }
        return jsonArray.toJavaList(String.class).stream()
                .filter(item -> item != null && !item.isBlank())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private JSONObject copyJsonObject(JSONObject jsonObject) {
        return jsonObject == null ? JSONObject.of() : JSONObject.from(jsonObject);
    }

    private String normalizeCodeType(String codeType) {
        return codeType.startsWith(".") ? codeType.substring(1) : codeType;
    }

    private String extractFileBaseName(String location, String ext) {
        String fileName = Path.of(location).getFileName().toString();
        String suffix = "." + ext;
        if (fileName.endsWith(suffix)) {
            return fileName.substring(0, fileName.length() - suffix.length());
        }
        return fileName;
    }

    private String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record TempDynamicActionRecord(
            String actionKey,
            String location,
            String cleanupActionId,
            ActionFileMetaData fileMetaData,
            MetaActionInfo metaActionInfo
    ) {
    }

}
