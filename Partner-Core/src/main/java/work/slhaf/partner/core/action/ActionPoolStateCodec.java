package work.slhaf.partner.core.action;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import work.slhaf.partner.core.action.entity.*;
import work.slhaf.partner.framework.agent.state.StateValue;
import work.slhaf.partner.module.action.executor.entity.HistoryAction;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
final class ActionPoolStateCodec {

    private ActionPoolStateCodec() {
    }

    static List<StateValue.Obj> encode(CopyOnWriteArraySet<ExecutableAction> actionPool) {
        return actionPool.stream()
                .map(ActionPoolStateCodec::encodeExecutableAction)
                .toList();
    }

    static CopyOnWriteArraySet<ExecutableAction> decode(@Nullable JSONArray actionPoolArray) {
        CopyOnWriteArraySet<ExecutableAction> restored = new CopyOnWriteArraySet<>();
        if (actionPoolArray == null) {
            return restored;
        }
        for (int i = 0; i < actionPoolArray.size(); i++) {
            JSONObject actionObject = actionPoolArray.getJSONObject(i);
            if (actionObject == null) {
                continue;
            }
            try {
                ExecutableAction executableAction = decodeExecutableAction(actionObject);
                if (executableAction != null) {
                    restored.add(executableAction);
                }
            } catch (Exception e) {
                log.warn("Skip invalid action_pool item at index {}", i, e);
            }
        }
        return restored;
    }

    private static StateValue.Obj encodeExecutableAction(ExecutableAction action) {
        Map<String, Object> actionMap = new LinkedHashMap<>();
        actionMap.put("kind", action instanceof SchedulableExecutableAction ? "schedulable" : "immediate");
        actionMap.put("uuid", action.getUuid());
        actionMap.put("source", action.getSource());
        actionMap.put("reason", action.getReason());
        actionMap.put("description", action.getDescription());
        actionMap.put("status", action.getStatus().name());
        actionMap.put("tendency", action.getTendency());
        actionMap.put("executing_stage", action.getExecutingStage());

        String result = resolveExecutableResult(action);
        if (result != null) {
            actionMap.put("result", result);
        }
        if (action instanceof SchedulableExecutableAction schedulableAction) {
            actionMap.put("schedule_type", schedulableAction.getScheduleType().name());
            actionMap.put("schedule_content", schedulableAction.getScheduleContent());
            actionMap.put("enabled", schedulableAction.getEnabled());
            actionMap.put("schedule_histories", encodeScheduleHistories(schedulableAction));
        }

        List<StateValue.Obj> chainStates = action.getActionChain().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    Map<String, Object> stageMap = new LinkedHashMap<>();
                    stageMap.put("stage", entry.getKey());
                    String stageDescription = action.getStageDescriptions().get(entry.getKey());
                    if (stageDescription != null && !stageDescription.isBlank()) {
                        stageMap.put("description", stageDescription);
                    }
                    stageMap.put("actions", entry.getValue().stream()
                            .map(ActionPoolStateCodec::encodeMetaAction)
                            .toList());
                    return StateValue.obj(stageMap);
                }).toList();
        actionMap.put("action_chain", chainStates);

        actionMap.put("history", encodeHistoryStages(action.getHistory()));

        return StateValue.obj(actionMap);
    }

    private static StateValue.Obj encodeMetaAction(MetaAction metaAction) {
        Map<String, Object> metaMap = new LinkedHashMap<>();
        metaMap.put("name", metaAction.getName());
        metaMap.put("io", metaAction.getIo());
        if (metaAction.getLauncher() != null) {
            metaMap.put("launcher", metaAction.getLauncher());
        }
        metaMap.put("type", metaAction.getType().name());
        metaMap.put("location", metaAction.getLocation());
        metaMap.put("params_json", JSONObject.toJSONString(metaAction.getParams()));
        metaMap.put("result_status", metaAction.getResult().getStatus().name());
        if (metaAction.getResult().getData() != null) {
            metaMap.put("result_data", metaAction.getResult().getData());
        }
        return StateValue.obj(metaMap);
    }

    private static StateValue.Obj encodeHistoryAction(HistoryAction historyAction) {
        Map<String, Object> historyMap = new LinkedHashMap<>();
        historyMap.put("action_key", historyAction.actionKey());
        historyMap.put("description", historyAction.description());
        historyMap.put("result", historyAction.result());
        return StateValue.obj(historyMap);
    }

    private static ExecutableAction decodeExecutableAction(JSONObject actionObject) {
        String kind = actionObject.getString("kind");
        String uuid = actionObject.getString("uuid");
        String source = actionObject.getString("source");
        String reason = actionObject.getString("reason");
        String description = actionObject.getString("description");
        String tendency = actionObject.getString("tendency");
        String status = actionObject.getString("status");
        Integer executingStage = actionObject.getInteger("executing_stage");
        if (kind == null || uuid == null || source == null || reason == null || description == null || tendency == null) {
            return null;
        }

        Map<Integer, String> restoredStageDescriptions = new LinkedHashMap<>();
        Map<Integer, List<MetaAction>> restoredChain = decodeActionChain(
                actionObject.getJSONArray("action_chain"),
                restoredStageDescriptions
        );
        ExecutableAction executableAction;
        if ("schedulable".equals(kind)) {
            String scheduleType = actionObject.getString("schedule_type");
            String scheduleContent = actionObject.getString("schedule_content");
            if (scheduleType == null || scheduleContent == null) {
                return null;
            }
            SchedulableExecutableAction schedulableAction = new SchedulableExecutableAction(
                    tendency,
                    restoredChain,
                    reason,
                    description,
                    source,
                    Schedulable.ScheduleType.valueOf(scheduleType),
                    scheduleContent,
                    uuid
            );
            Boolean enabled = actionObject.getBoolean("enabled");
            if (enabled != null) {
                schedulableAction.setEnabled(enabled);
            }
            schedulableAction.getScheduleHistories().addAll(decodeScheduleHistories(actionObject.getJSONArray("schedule_histories")));
            executableAction = schedulableAction;
        } else if ("immediate".equals(kind)) {
            executableAction = new ImmediateExecutableAction(
                    tendency,
                    restoredChain,
                    reason,
                    description,
                    source,
                    uuid
            );
        } else {
            return null;
        }

        if (status != null) {
            executableAction.setStatus(Action.Status.valueOf(status));
        }
        if (executingStage != null) {
            executableAction.setExecutingStage(executingStage);
        }
        String result = actionObject.getString("result");
        if (result != null) {
            executableAction.setResult(result);
        }
        executableAction.getStageDescriptions().putAll(restoredStageDescriptions);
        executableAction.getHistory().putAll(decodeHistory(actionObject.getJSONArray("history")));
        return executableAction;
    }

    private static Map<Integer, List<MetaAction>> decodeActionChain(
            @Nullable JSONArray actionChainArray,
            Map<Integer, String> stageDescriptions
    ) {
        Map<Integer, List<MetaAction>> restored = new LinkedHashMap<>();
        if (actionChainArray == null) {
            return toMutableActionChain(restored);
        }
        for (int i = 0; i < actionChainArray.size(); i++) {
            JSONObject stageObject = actionChainArray.getJSONObject(i);
            if (stageObject == null) {
                continue;
            }
            Integer stage = stageObject.getInteger("stage");
            String description = stageObject.getString("description");
            JSONArray actions = stageObject.getJSONArray("actions");
            if (stage == null || actions == null) {
                continue;
            }
            if (description != null && !description.isBlank()) {
                stageDescriptions.put(stage, description);
            }
            List<MetaAction> metaActions = new ArrayList<>();
            for (int j = 0; j < actions.size(); j++) {
                JSONObject actionObject = actions.getJSONObject(j);
                MetaAction metaAction = decodeMetaAction(actionObject);
                if (metaAction != null) {
                    metaActions.add(metaAction);
                }
            }
            restored.put(stage, metaActions);
        }
        return toMutableActionChain(restored);
    }

    private static MetaAction decodeMetaAction(@Nullable JSONObject actionObject) {
        if (actionObject == null) {
            return null;
        }
        String name = actionObject.getString("name");
        Boolean io = actionObject.getBoolean("io");
        String type = actionObject.getString("type");
        String location = actionObject.getString("location");
        if (name == null || io == null || type == null || location == null) {
            return null;
        }
        MetaAction metaAction = new MetaAction(
                name,
                io,
                actionObject.getString("launcher"),
                MetaAction.Type.valueOf(type),
                location
        );
        String paramsJson = actionObject.getString("params_json");
        if (paramsJson != null && !paramsJson.isBlank()) {
            JSONObject paramsObject = JSONObject.parseObject(paramsJson);
            if (paramsObject != null) {
                metaAction.getParams().putAll(paramsObject);
            }
        }
        String resultStatus = actionObject.getString("result_status");
        if (resultStatus != null) {
            metaAction.getResult().setStatus(MetaAction.Result.Status.valueOf(resultStatus));
        }
        metaAction.getResult().setData(actionObject.getString("result_data"));
        return metaAction;
    }

    private static Map<Integer, List<HistoryAction>> decodeHistory(@Nullable JSONArray historyArray) {
        Map<Integer, List<HistoryAction>> restored = new LinkedHashMap<>();
        if (historyArray == null) {
            return restored;
        }
        for (int i = 0; i < historyArray.size(); i++) {
            JSONObject stageObject = historyArray.getJSONObject(i);
            if (stageObject == null) {
                continue;
            }
            Integer stage = stageObject.getInteger("stage");
            JSONArray actions = stageObject.getJSONArray("actions");
            if (stage == null || actions == null) {
                continue;
            }
            List<HistoryAction> historyActions = new ArrayList<>();
            for (int j = 0; j < actions.size(); j++) {
                JSONObject historyObject = actions.getJSONObject(j);
                if (historyObject == null) {
                    continue;
                }
                String actionKey = historyObject.getString("action_key");
                String description = historyObject.getString("description");
                String result = historyObject.getString("result");
                if (actionKey == null || description == null || result == null) {
                    continue;
                }
                historyActions.add(new HistoryAction(actionKey, description, result));
            }
            restored.put(stage, historyActions);
        }
        return restored;
    }

    private static List<StateValue.Obj> encodeHistoryStages(Map<Integer, ? extends List<HistoryAction>> historyMap) {
        return historyMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    Map<String, Object> stageMap = new LinkedHashMap<>();
                    stageMap.put("stage", entry.getKey());
                    stageMap.put("actions", entry.getValue().stream()
                            .map(ActionPoolStateCodec::encodeHistoryAction)
                            .toList());
                    return StateValue.obj(stageMap);
                }).toList();
    }

    private static List<StateValue.Obj> encodeScheduleHistories(SchedulableExecutableAction schedulableAction) {
        return schedulableAction.getScheduleHistories().stream()
                .map(scheduleHistory -> {
                    Map<String, Object> historyMap = new LinkedHashMap<>();
                    historyMap.put("end_time", scheduleHistory.getEndTime().toString());
                    historyMap.put("result", scheduleHistory.getResult());
                    historyMap.put("history", encodeHistoryStages(scheduleHistory.getHistory()));
                    return StateValue.obj(historyMap);
                })
                .toList();
    }

    private static List<SchedulableExecutableAction.ScheduleHistory> decodeScheduleHistories(@Nullable JSONArray scheduleHistoriesArray) {
        List<SchedulableExecutableAction.ScheduleHistory> restored = new ArrayList<>();
        if (scheduleHistoriesArray == null) {
            return restored;
        }
        for (int i = 0; i < scheduleHistoriesArray.size(); i++) {
            JSONObject historyObject = scheduleHistoriesArray.getJSONObject(i);
            if (historyObject == null) {
                continue;
            }
            try {
                String endTime = historyObject.getString("end_time");
                String result = historyObject.getString("result");
                if (endTime == null || result == null) {
                    continue;
                }
                restored.add(new SchedulableExecutableAction.ScheduleHistory(
                        ZonedDateTime.parse(endTime),
                        result,
                        decodeHistory(historyObject.getJSONArray("history"))
                ));
            } catch (Exception e) {
                log.warn("Skip invalid schedule_history item at index {}", i, e);
            }
        }
        return restored;
    }

    private static Map<Integer, List<MetaAction>> toMutableActionChain(Map<Integer, List<MetaAction>> actionChain) {
        Map<Integer, List<MetaAction>> restored = new LinkedHashMap<>();
        actionChain.forEach((stage, actions) -> restored.put(stage, new ArrayList<>(actions)));
        return restored;
    }

    private static String resolveExecutableResult(ExecutableAction action) {
        try {
            return action.getResult();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
