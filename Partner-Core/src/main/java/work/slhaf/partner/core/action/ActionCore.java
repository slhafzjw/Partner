package work.slhaf.partner.core.action;

import com.alibaba.fastjson2.JSONObject;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.slhaf.partner.core.action.entity.ExecutableAction;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.entity.intervention.InterventionType;
import work.slhaf.partner.core.action.entity.intervention.MetaIntervention;
import work.slhaf.partner.core.action.exception.ActionLookupException;
import work.slhaf.partner.core.action.runner.LocalRunnerClient;
import work.slhaf.partner.core.action.runner.RunnerClient;
import work.slhaf.partner.framework.agent.config.ConfigCenter;
import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;
import work.slhaf.partner.framework.agent.exception.ExceptionReporterHandler;
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.framework.agent.factory.context.Shutdown;
import work.slhaf.partner.framework.agent.state.State;
import work.slhaf.partner.framework.agent.state.StateSerializable;
import work.slhaf.partner.framework.agent.state.StateValue;
import work.slhaf.partner.framework.agent.support.Result;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@SuppressWarnings("FieldMayBeFinal")
@CapabilityCore(value = "action")
@Slf4j
public class ActionCore implements StateSerializable {
    public static final String BUILTIN_LOCATION = "builtin";
    public static final String ORIGIN_LOCATION = "origin";

    // 由于当前的执行器逻辑实现，平台线程池大小不得小于 2，这里规定为最小为 4
    private final ExecutorService platformExecutor = Executors.newFixedThreadPool(Math.max(Runtime.getRuntime().availableProcessors(), 4));
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 已存在的行动程序，键格式为‘<MCP-ServerName>::<Tool-Name>’，值为 MCP Server 通过 Resources 相关渠道传递的行动程序元信息
     */
    private final ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
    /**
     * 持久行动池
     */
    private CopyOnWriteArraySet<ExecutableAction> actionPool = new CopyOnWriteArraySet<>();

    private RunnerClient runnerClient;

    public ActionCore() throws IOException, ClassNotFoundException {
        String baseActionPath = ConfigCenter.INSTANCE.getPaths().getResourcesDir().resolve("action").normalize().toAbsolutePath().toString();
        // TODO 通过 Config 指定采用何种 runnerClient，当前只提供 LocalRunnerClient
        runnerClient = new LocalRunnerClient(existedMetaActions, virtualExecutor, baseActionPath);
        register();
    }

    @Shutdown
    public void shutdown() {
        try {
            runnerClient.close();
        } catch (Exception e) {
            log.warn("runner client close error", e);
        }
        try {
            platformExecutor.shutdown();
            virtualExecutor.shutdown();

            int count = 0;
            if (!platformExecutor.awaitTermination(8, TimeUnit.SECONDS)) {
                count += platformExecutor.shutdownNow().size();
            }
            if (!virtualExecutor.awaitTermination(8, TimeUnit.SECONDS)) {
                count += virtualExecutor.shutdownNow().size();
            }
            if (count != 0) {
                log.warn("{} tasks still running", count);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @CapabilityMethod
    public void putAction(@NonNull ExecutableAction executableAction) {
        actionPool.removeIf(data -> data.getUuid().equals(executableAction.getUuid())); // 用来应对 ScheduledActionData 的重新排列
        actionPool.add(executableAction);
    }

    @CapabilityMethod
    public Set<ExecutableAction> listActions(@Nullable ExecutableAction.Status status, @Nullable String source) {
        return actionPool.stream()
                .filter(actionData -> status == null || actionData.getStatus().equals(status))
                .filter(actionData -> source == null || actionData.getSource().equals(source))
                .collect(Collectors.toSet());
    }

    @CapabilityMethod
    public ExecutorService getExecutor(ExecutorType type) {
        return switch (type) {
            case VIRTUAL -> virtualExecutor;
            case PLATFORM -> platformExecutor;
        };
    }

    @CapabilityMethod
    public void registerMetaActions(@NonNull Map<String, MetaActionInfo> metaActions) {
        existedMetaActions.putAll(metaActions);
    }

    @CapabilityMethod
    public Map<String, MetaActionInfo> listAvailableMetaActions() {
        return existedMetaActions;
    }

    @CapabilityMethod
    public Result<MetaAction> loadMetaAction(@NonNull String actionKey) {
        MetaActionInfo metaActionInfo = existedMetaActions.get(actionKey);
        if (metaActionInfo == null) {
            return Result.failure(new ActionLookupException(
                    "Meta action info not found for action key: " + actionKey,
                    actionKey,
                    "META_ACTION"
            ));
        }

        String[] split = actionKey.split("::", 2);
        if (split.length < 2) {
            return Result.failure(new ActionLookupException(
                    "Invalid action key format: " + actionKey,
                    actionKey,
                    "META_ACTION"
            ));
        }
        MetaAction.Type type = switch (split[0]) {
            case BUILTIN_LOCATION -> MetaAction.Type.BUILTIN;
            case ORIGIN_LOCATION -> MetaAction.Type.ORIGIN;
            default -> MetaAction.Type.MCP;
        };
        return Result.success(new MetaAction(
                split[1],
                metaActionInfo.getIo(),
                metaActionInfo.getLauncher(),
                type,
                split[0]
        ));
    }

    @CapabilityMethod
    public Result<MetaActionInfo> loadMetaActionInfo(@NonNull String actionKey) {
        MetaActionInfo info = existedMetaActions.get(actionKey);
        if (info == null) {
            return Result.failure(new ActionLookupException(
                    "Meta action description not found for action key: " + actionKey,
                    actionKey,
                    "META_ACTION_INFO"
            ));
        }
        return Result.success(info);
    }

    @CapabilityMethod
    public boolean checkExists(String... actionKeys) {
        return existedMetaActions.keySet().containsAll(Arrays.asList(actionKeys));
    }

    @CapabilityMethod
    public RunnerClient runnerClient() {
        return runnerClient;
    }

    @CapabilityMethod
    public void handleInterventions(List<MetaIntervention> interventions, ExecutableAction executableAction) {
        // 加载数据
        if (executableAction == null) {
            return;
        }
        // 加锁确保同步
        synchronized (executableAction.getExecutionLock()) {
            applyInterventions(interventions, executableAction);
        }
    }

    private void applyInterventions(List<MetaIntervention> interventions, ExecutableAction executableAction) {
        boolean[] rebuildCleanTag = {false};

        interventions.sort(Comparator.comparingInt(MetaIntervention::getOrder));

        for (MetaIntervention intervention : interventions) {
            Result<List<MetaAction>> actionsResult = resolveInterventionActions(intervention);
            actionsResult
                    .onFailure(ExceptionReporterHandler.INSTANCE::report)
                    .onSuccess(actions -> {
                        switch (intervention.getType()) {
                            case InterventionType.APPEND ->
                                    handleAppend(executableAction, intervention.getOrder(), actions);
                            case InterventionType.INSERT ->
                                    handleInsert(executableAction, intervention.getOrder(), actions);
                            case InterventionType.DELETE ->
                                    handleDelete(executableAction, intervention.getOrder(), actions);
                            case InterventionType.CANCEL -> handleCancel(executableAction);
                            case InterventionType.REBUILD -> {
                                if (!rebuildCleanTag[0]) {
                                    cleanActionData(executableAction);
                                    rebuildCleanTag[0] = true;
                                }
                                handleRebuild(executableAction, intervention.getOrder(), actions);
                            }
                        }
                    });
        }

    }

    private Result<List<MetaAction>> resolveInterventionActions(MetaIntervention intervention) {
        List<MetaAction> actions = new ArrayList<>();
        for (String actionKey : intervention.getActions()) {
            Result<MetaAction> metaActionResult = loadMetaAction(actionKey);
            AgentRuntimeException failure = metaActionResult.onSuccess(actions::add).exceptionOrNull();
            if (failure != null) {
                return Result.failure(failure);
            }
        }
        return Result.success(actions);
    }

    /**
     * 在未进入执行阶段的行动单元组新增新的行动
     */
    private void handleAppend(ExecutableAction executableAction, int order, List<MetaAction> actions) {
        if (order <= executableAction.getExecutingStage())
            return;

        executableAction.getActionChain().put(order, actions);
    }

    /**
     * 在未进入执行阶段和正处于行动阶段的行动单元组插入新的行动
     */
    private void handleInsert(ExecutableAction executableAction, int order, List<MetaAction> actions) {
        if (order < executableAction.getExecutingStage())
            return;

        List<MetaAction> stageActions = executableAction.getActionChain().computeIfAbsent(order, k -> new ArrayList<>());
        synchronized (stageActions) {
            stageActions.addAll(actions);
        }
    }

    private void handleDelete(ExecutableAction executableAction, int order, List<MetaAction> actions) {
        if (order <= executableAction.getExecutingStage())
            return;

        Map<Integer, List<MetaAction>> actionChain = executableAction.getActionChain();
        if (actionChain.containsKey(order)) {
            actionChain.get(order).removeAll(actions);
            if (actionChain.get(order).isEmpty()) {
                actionChain.remove(order);
            }
        }
    }

    private void handleCancel(ExecutableAction executableAction) {
        executableAction.setStatus(ExecutableAction.Status.FAILED);
        executableAction.setResult("行动取消");
    }

    private void handleRebuild(ExecutableAction executableAction, int order, List<MetaAction> actions) {
        Map<Integer, List<MetaAction>> actionChain = executableAction.getActionChain();
        actionChain.put(order, actions);
    }

    private void cleanActionData(ExecutableAction executableAction) {
        executableAction.getActionChain().clear();
        executableAction.setExecutingStage(0);
        executableAction.setStatus(ExecutableAction.Status.PREPARE);
        executableAction.getHistory().clear();
    }

    @Override
    public @NotNull Path statePath() {
        return Path.of("core", "action.json");
    }

    @Override
    public void load(@NotNull JSONObject state) {
        actionPool = ActionPoolStateCodec.decode(state.getJSONArray("action_pool"));
    }

    @Override
    public @NotNull State convert() {
        State state = new State();
        List<StateValue.Obj> actionPoolState = ActionPoolStateCodec.encode(actionPool);
        state.append("action_pool", StateValue.arr(actionPoolState));
        return state;
    }

    public enum ExecutorType {
        VIRTUAL, PLATFORM
    }

}
