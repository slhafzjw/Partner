package work.slhaf.partner.core.action;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.Nullable;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.api.agent.runtime.config.ConfigCenter;
import work.slhaf.partner.core.PartnerCore;
import work.slhaf.partner.core.action.entity.ExecutableAction;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.entity.intervention.InterventionType;
import work.slhaf.partner.core.action.entity.intervention.MetaIntervention;
import work.slhaf.partner.core.action.exception.MetaActionNotFoundException;
import work.slhaf.partner.core.action.runner.LocalRunnerClient;
import work.slhaf.partner.core.action.runner.RunnerClient;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SuppressWarnings("FieldMayBeFinal")
@CapabilityCore(value = "action")
@Slf4j
public class ActionCore extends PartnerCore<ActionCore> {
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
        setupShutdownHook();
    }

    private void setupShutdownHook() {
        // 将执行中的行动状态置为失败
        val executingActionSet = listActions(ExecutableAction.Status.EXECUTING, null);
        for (ExecutableAction executableAction : executingActionSet) {
            executableAction.setStatus(ExecutableAction.Status.FAILED);
            executableAction.setResult("由于系统中断而失败");
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
    public MetaAction loadMetaAction(@NonNull String actionKey) {
        MetaActionInfo metaActionInfo = existedMetaActions.get(actionKey);
        if (metaActionInfo == null) {
            throw new MetaActionNotFoundException("未找到对应的行动程序信息" + actionKey);
        }

        String[] split = actionKey.split("::", 2);
        if (split.length < 2) {
            throw new MetaActionNotFoundException("未找到对应的行动程序，原因: 传入的 actionKey(" + actionKey + ") 存在异常");
        }
        MetaAction.Type type = switch (split[0]) {
            case BUILTIN_LOCATION -> MetaAction.Type.BUILTIN;
            case ORIGIN_LOCATION -> MetaAction.Type.ORIGIN;
            default -> MetaAction.Type.MCP;
        };
        return new MetaAction(
                split[1],
                metaActionInfo.getIo(),
                metaActionInfo.getLauncher(),
                type,
                split[0]
        );
    }

    @CapabilityMethod
    public MetaActionInfo loadMetaActionInfo(@NonNull String actionKey) {
        MetaActionInfo info = existedMetaActions.get(actionKey);
        if (info == null) {
            throw new MetaActionNotFoundException("未找到对应的行动程序描述信息: " + actionKey);
        }
        return info;
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
        synchronized (executableAction.getStatus()) {
            applyInterventions(interventions, executableAction);
        }
    }

    private void applyInterventions(List<MetaIntervention> interventions, ExecutableAction executableAction) {
        boolean rebuildCleanTag = false;

        interventions.sort(Comparator.comparingInt(MetaIntervention::getOrder));

        for (MetaIntervention intervention : interventions) {
            List<MetaAction> actions = intervention.getActions()
                    .stream()
                    .map(this::loadMetaAction)
                    .toList();

            switch (intervention.getType()) {
                case InterventionType.APPEND -> handleAppend(executableAction, intervention.getOrder(), actions);
                case InterventionType.INSERT -> handleInsert(executableAction, intervention.getOrder(), actions);
                case InterventionType.DELETE -> handleDelete(executableAction, intervention.getOrder(), actions);
                case InterventionType.CANCEL -> handleCancel(executableAction);
                case InterventionType.REBUILD -> {
                    if (!rebuildCleanTag) {
                        cleanActionData(executableAction);
                        rebuildCleanTag = true;
                    }
                    handleRebuild(executableAction, intervention.getOrder(), actions);
                }
            }
        }

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

        executableAction.getActionChain().computeIfAbsent(order, k -> new ArrayList<>()).addAll(actions);
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
    protected String getCoreKey() {
        return "action-core";
    }

    public enum ExecutorType {
        VIRTUAL, PLATFORM
    }

}
