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
        Map<String, StateValue> actionMap = new LinkedHashMap<>();
        actionMap.put("kind", StateValue.str(action instanceof SchedulableExecutableAction ? "schedulable" : "immediate"));
        actionMap.put("uuid", StateValue.str(action.getUuid()));
        actionMap.put("source", StateValue.str(action.getSource()));
        actionMap.put("reason", StateValue.str(action.getReason()));
        actionMap.put("description", StateValue.str(action.getDescription()));
        actionMap.put("status", StateValue.str(action.getStatus().name()));
        actionMap.put("tendency", StateValue.str(action.getTendency()));
        actionMap.put("executing_stage", StateValue.num(action.getExecutingStage()));

        String result = resolveExecutableResult(action);
        if (result != null) {
            actionMap.put("result", StateValue.str(result));
        }
        if (action instanceof SchedulableExecutableAction schedulableAction) {
            actionMap.put("schedule_type", StateValue.str(schedulableAction.getScheduleType().name()));
            actionMap.put("schedule_content", StateValue.str(schedulableAction.getScheduleContent()));
            actionMap.put("enabled", StateValue.bool(schedulableAction.getEnabled()));
            actionMap.put("schedule_histories", StateValue.arr(encodeScheduleHistories(schedulableAction)));
        }

        List<StateValue> chainStates = action.getActionChain().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .<StateValue>map(entry -> {
                    Map<String, StateValue> stageMap = new LinkedHashMap<>();
                    stageMap.put("stage", StateValue.num(entry.getKey()));
                    String stageDescription = action.getStageDescriptions().get(entry.getKey());
                    if (stageDescription != null && !stageDescription.isBlank()) {
                        stageMap.put("description", StateValue.str(stageDescription));
                    }
                    stageMap.put("actions", StateValue.arr(entry.getValue().stream()
                            .map(metaAction -> (StateValue) encodeMetaAction(metaAction))
                            .toList()));
                    return StateValue.obj(stageMap);
                }).toList();
        actionMap.put("action_chain", StateValue.arr(chainStates));

        actionMap.put("history", StateValue.arr(encodeHistoryStages(action.getHistory())));

        return StateValue.obj(actionMap);
    }

    private static StateValue.Obj encodeMetaAction(MetaAction metaAction) {
        Map<String, StateValue> metaMap = new LinkedHashMap<>();
        metaMap.put("name", StateValue.str(metaAction.getName()));
        metaMap.put("io", StateValue.bool(metaAction.getIo()));
        if (metaAction.getLauncher() != null) {
            metaMap.put("launcher", StateValue.str(metaAction.getLauncher()));
        }
        metaMap.put("type", StateValue.str(metaAction.getType().name()));
        metaMap.put("location", StateValue.str(metaAction.getLocation()));
        metaMap.put("params_json", StateValue.str(JSONObject.toJSONString(metaAction.getParams())));
        metaMap.put("result_status", StateValue.str(metaAction.getResult().getStatus().name()));
        if (metaAction.getResult().getData() != null) {
            metaMap.put("result_data", StateValue.str(metaAction.getResult().getData()));
        }
        return StateValue.obj(metaMap);
    }

    private static StateValue.Obj encodeHistoryAction(HistoryAction historyAction) {
        Map<String, StateValue> historyMap = new LinkedHashMap<>();
        historyMap.put("action_key", StateValue.str(historyAction.actionKey()));
        historyMap.put("description", StateValue.str(historyAction.description()));
        historyMap.put("result", StateValue.str(historyAction.result()));
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

    private static List<StateValue> encodeHistoryStages(Map<Integer, ? extends List<HistoryAction>> historyMap) {
        return historyMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .<StateValue>map(entry -> {
                    Map<String, StateValue> stageMap = new LinkedHashMap<>();
                    stageMap.put("stage", StateValue.num(entry.getKey()));
                    stageMap.put("actions", StateValue.arr(entry.getValue().stream()
                            .map(historyAction -> (StateValue) encodeHistoryAction(historyAction))
                            .toList()));
                    return StateValue.obj(stageMap);
                }).toList();
    }

    private static List<StateValue> encodeScheduleHistories(SchedulableExecutableAction schedulableAction) {
        return schedulableAction.getScheduleHistories().stream()
                .<StateValue>map(scheduleHistory -> {
                    Map<String, StateValue> historyMap = new LinkedHashMap<>();
                    historyMap.put("end_time", StateValue.str(scheduleHistory.getEndTime().toString()));
                    historyMap.put("result", StateValue.str(scheduleHistory.getResult()));
                    historyMap.put("history", StateValue.arr(encodeHistoryStages(scheduleHistory.getHistory())));
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
