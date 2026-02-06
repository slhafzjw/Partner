package work.slhaf.partner.core.action;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.common.vector.VectorClient;
import work.slhaf.partner.core.PartnerCore;
import work.slhaf.partner.core.action.entity.*;
import work.slhaf.partner.core.action.entity.cache.ActionCacheData;
import work.slhaf.partner.core.action.entity.cache.CacheAdjustData;
import work.slhaf.partner.core.action.entity.cache.CacheAdjustMetaData;
import work.slhaf.partner.core.action.exception.ActionDataNotFoundException;
import work.slhaf.partner.core.action.exception.MetaActionNotFoundException;
import work.slhaf.partner.core.action.runner.RunnerClient;
import work.slhaf.partner.core.action.runner.SandboxRunnerClient;
import work.slhaf.partner.module.modules.action.interventor.entity.InterventionType;
import work.slhaf.partner.module.modules.action.interventor.entity.MetaIntervention;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@SuppressWarnings("FieldMayBeFinal")
@CapabilityCore(value = "action")
@Slf4j
public class ActionCore extends PartnerCore<ActionCore> {

    /**
     * 持久行动池，以用户id为键存储所有状态的任务
     */
    private HashMap<String, List<ActionData>> actionPool = new HashMap<>();// TODO 考虑是否取消用户分池

    /**
     * 待确认任务，以userId区分不同用户，因为需要跨请求确认
     */
    private HashMap<String, List<ActionData>> pendingActions = new HashMap<>();

    /**
     * 语义缓存与行为倾向映射
     */
    private List<ActionCacheData> actionCache = new ArrayList<>();

    private final Lock cacheLock = new ReentrantLock();

    // 由于当前的执行器逻辑实现，平台线程池大小不得小于 2，这里规定为最小为 4
    private final ExecutorService platformExecutor = Executors
            .newFixedThreadPool(Math.max(Runtime.getRuntime().availableProcessors(), 4));
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 已存在的行动程序，键格式为‘<MCP-ServerName>::<Tool-Name>’，值为 MCP Server 通过 Resources 相关渠道传递的行动程序元信息
     */
    private final ConcurrentHashMap<String, MetaActionInfo> existedMetaActions = new ConcurrentHashMap<>();
    private final List<PhaserRecord> phaserRecords = new ArrayList<>();
    private RunnerClient runnerClient;

    public ActionCore() throws IOException, ClassNotFoundException {
        // TODO 通过 AgentConfigManager指定采用何种 runnerClient 
        runnerClient = new SandboxRunnerClient(existedMetaActions, virtualExecutor);
        setupShutdownHook();
    }

    private void setupShutdownHook() {
        // 将执行中的行动状态置为失败
        List<ActionData> executingActionList = listExecutingAction();
        for (ActionData actionData : executingActionList) {
            actionData.setStatus(ActionData.ActionStatus.FAILED);
            actionData.setResult("由于系统中断而失败");
        }
    }

    private List<ActionData> listExecutingAction() {
        return actionPool.values().stream()
                .flatMap(Collection::stream)
                .filter(action -> action.getStatus() == ActionData.ActionStatus.EXECUTING)
                .collect(Collectors.toList());
    }

    @CapabilityMethod
    public synchronized void putPendingActions(String userId, ActionData actionData) {
        pendingActions.computeIfAbsent(userId, k -> {
            List<ActionData> temp = new ArrayList<>();
            temp.add(actionData);
            return temp;
        });
    }

    @CapabilityMethod
    public synchronized List<ActionData> popPendingAction(String userId) {
        List<ActionData> infos = pendingActions.get(userId);
        pendingActions.remove(userId);
        return infos;
    }

    @CapabilityMethod
    public synchronized void putPreparedAction(String uuid, ActionData actionData) {
        actionPool.computeIfAbsent(uuid, k -> {
            List<ActionData> temp = new ArrayList<>();
            temp.add(actionData);
            return temp;
        });
    }

    @CapabilityMethod
    public List<ActionData> listPreparedAction(String userId) {
        List<ActionData> actions = actionPool.get(userId);
        return actions.stream()
                .filter(actionData -> actionData.getStatus().equals(ActionData.ActionStatus.PREPARE))
                .toList();
    }

    @CapabilityMethod
    public List<ActionData> listPendingAction(String userId) {
        return pendingActions.get(userId);
    }

    /**
     * 计算输入内容的语义向量，根据与{@link ActionCacheData#getInputVector()}的相似度挑取缓存，后续将根据评估结果来更新计数
     *
     * @param input 本次输入内容
     * @return 命中的行为倾向集合
     */
    @CapabilityMethod
    public List<String> selectTendencyCache(String input) {
        if (!VectorClient.status) {
            return null;
        }
        VectorClient vectorClient = VectorClient.INSTANCE;
        // 计算本次输入的向量
        float[] vector = vectorClient.compute(input);
        if (vector == null)
            return null;
        // 与现有缓存比对，将匹配到的收集并返回
        return actionCache.parallelStream()
                .filter(ActionCacheData::isActivated)
                .filter(data -> {
                    double compared = vectorClient.compare(vector, data.getInputVector());
                    return compared > data.getThreshold();
                })
                .map(ActionCacheData::getTendency)
                .collect(Collectors.toList());
    }

    @CapabilityMethod
    public void updateTendencyCache(CacheAdjustData data) {
        VectorClient vectorClient = VectorClient.INSTANCE;
        List<CacheAdjustMetaData> list = data.getMetaDataList();
        String input = data.getInput();
        float[] inputVector = vectorClient.compute(input);

        List<CacheAdjustMetaData> matchAndPassed = new ArrayList<>();
        List<CacheAdjustMetaData> matchNotPassed = new ArrayList<>();
        List<CacheAdjustMetaData> notMatchPassed = new ArrayList<>();

        for (CacheAdjustMetaData metaData : list) {
            if (metaData.isHit() && metaData.isPassed()) {
                matchAndPassed.add(metaData);
            } else if (metaData.isHit()) {
                matchNotPassed.add(metaData);
            } else if (!metaData.isPassed()) {
                notMatchPassed.add(metaData);
            }
        }

        platformExecutor.execute(() -> adjustMatchAndPassed(matchAndPassed, inputVector, input, vectorClient));
        platformExecutor.execute(() -> adjustMatchNotPassed(matchNotPassed, vectorClient));
        platformExecutor.execute(() -> adjustNotMatchPassed(notMatchPassed, inputVector, input, vectorClient));
    }

    @CapabilityMethod
    public ExecutorService getExecutor(ExecutorType type) {
        return switch (type) {
            case VIRTUAL -> virtualExecutor;
            case PLATFORM -> platformExecutor;
        };
    }

    @CapabilityMethod
    public Map<String, MetaActionInfo> listAvailableActions() {
        return existedMetaActions;
    }

    @CapabilityMethod
    public synchronized PhaserRecord putPhaserRecord(Phaser phaser, ActionData actionData) {
        PhaserRecord record = new PhaserRecord(phaser, actionData);
        phaserRecords.add(record);
        return record;
    }

    @CapabilityMethod
    public synchronized void removePhaserRecord(Phaser phaser) {
        PhaserRecord remove = null;
        for (PhaserRecord record : phaserRecords) {
            if (record.phaser().equals(phaser)) {
                remove = record;
            }
        }

        if (remove != null) {
            phaserRecords.remove(remove);
        }
    }

    @CapabilityMethod
    public PhaserRecord getPhaserRecord(String tendency, String source) {
        for (PhaserRecord record : phaserRecords) {
            ActionData data = record.actionData();
            if (data.getTendency().equals(tendency) && data.getSource().equals(source)) {
                return record;
            }
        }
        throw new ActionDataNotFoundException("未找到对应的 Phaser 记录: tendency=" + tendency + ", source=" + source);
    }

    @CapabilityMethod
    public MetaAction loadMetaAction(@NonNull String actionKey) {
        MetaActionInfo metaActionInfo = existedMetaActions.get(actionKey);
        if (metaActionInfo == null) {
            throw new MetaActionNotFoundException("未找到对应的行动程序信息" + actionKey);
        }
        MetaAction metaAction = new MetaAction();
        metaAction.setParams(metaActionInfo.getParams());
        metaAction.setType(MetaActionType.MCP);
        metaAction.setIo(metaActionInfo.isIo());
        String[] split = actionKey.split("::");
        if (split.length < 2) {
            throw new MetaActionNotFoundException("未找到对应的行动程序，原因: 传入的 actionKey(" + actionKey + ") 存在异常");
        }
        metaAction.setLocation(split[0]);
        metaAction.setName(split[1]);
        return metaAction;
    }

    @CapabilityMethod
    public List<PhaserRecord> listPhaserRecords() {
        return phaserRecords;
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
    public <T> void handleInterventions(List<MetaIntervention> interventions, T data) {
        // 加载数据
        Phaser phaser = null;
        ActionData actionData = switch (data) {
            case PhaserRecord record -> {
                phaser = record.phaser();
                yield record.actionData();
            }
            case ActionData tempData -> tempData;
            default -> null;
        };
        if (actionData == null) {
            return;
        }

        // 加锁确保同步
        synchronized (actionData.getStatus()) {
            applyInterventions(interventions, actionData);
        }
    }

    private void applyInterventions(List<MetaIntervention> interventions, ActionData actionData) {
        boolean rebuildCleanTag = false;

        interventions.sort(Comparator.comparingInt(MetaIntervention::getOrder));

        for (MetaIntervention intervention : interventions) {
            List<MetaAction> actions = intervention.getActions()
                    .stream()
                    .map(this::loadMetaAction)
                    .toList();

            switch (intervention.getType()) {
                case InterventionType.APPEND -> handleAppend(actionData, intervention.getOrder(), actions);
                case InterventionType.INSERT -> handleInsert(actionData, intervention.getOrder(), actions);
                case InterventionType.DELETE -> handleDelete(actionData, intervention.getOrder(), actions);
                case InterventionType.CANCEL -> handleCancel(actionData);
                case InterventionType.REBUILD -> {
                    if (!rebuildCleanTag) {
                        cleanActionData(actionData);
                        rebuildCleanTag = true;
                    }
                    handleRebuild(actionData, intervention.getOrder(), actions);
                }
            }
        }

    }

    /**
     * 在未进入执行阶段的行动单元组新增新的行动
     */
    private void handleAppend(ActionData actionData, int order, List<MetaAction> actions) {
        if (order <= actionData.getExecutingStage())
            return;

        actionData.getActionChain().put(order, actions);
    }

    /**
     * 在未进入执行阶段和正处于行动阶段的行动单元组插入新的行动
     */
    private void handleInsert(ActionData actionData, int order, List<MetaAction> actions) {
        if (order < actionData.getExecutingStage())
            return;

        actionData.getActionChain().computeIfAbsent(order, k -> new ArrayList<>()).addAll(actions);
    }

    private void handleDelete(ActionData actionData, int order, List<MetaAction> actions) {
        if (order <= actionData.getExecutingStage())
            return;

        Map<Integer, List<MetaAction>> actionChain = actionData.getActionChain();
        if (actionChain.containsKey(order)) {
            actionChain.get(order).removeAll(actions);
            if (actionChain.get(order).isEmpty()) {
                actionChain.remove(order);
            }
        }
    }

    private void handleCancel(ActionData actionData) {
        actionData.setStatus(ActionData.ActionStatus.FAILED);
        actionData.setResult("行动取消");
    }

    private void handleRebuild(ActionData actionData, int order, List<MetaAction> actions) {
        Map<Integer, List<MetaAction>> actionChain = actionData.getActionChain();
        actionChain.put(order, actions);
    }

    private void cleanActionData(ActionData actionData) {
        actionData.getActionChain().clear();
        actionData.setExecutingStage(0);
        actionData.setStatus(ActionData.ActionStatus.PREPARE);
        actionData.getHistory().clear();
    }

    /**
     * 命中缓存且评估通过时
     *
     * @param matchAndPassed 该类型的带调整缓存信息列表
     * @param inputVector    本次输入内容的语义向量
     * @param vectorClient   向量客户端
     */
    private void adjustMatchAndPassed(List<CacheAdjustMetaData> matchAndPassed, float[] inputVector, String input,
                                      VectorClient vectorClient) {
        matchAndPassed.forEach(adjustData -> {
            // 获取原始缓存条目
            String tendency = adjustData.getTendency();
            ActionCacheData primaryCacheData = selectCacheData(tendency);
            if (primaryCacheData == null) {
                return;
            }
            primaryCacheData.updateAfterMatchAndPassed(inputVector, vectorClient, input);
        });
    }

    /**
     * 针对命中缓存、但评估未通过的条目与输入进行处理
     *
     * @param matchNotPassed 该类型的带调整缓存信息列表
     * @param vectorClient   向量客户端
     */
    private void adjustMatchNotPassed(List<CacheAdjustMetaData> matchNotPassed, VectorClient vectorClient) {
        List<ActionCacheData> toRemove = new ArrayList<>();
        matchNotPassed.forEach(adjustData -> {
            // 获取原始缓存条目
            String tendency = adjustData.getTendency();
            ActionCacheData primaryCacheData = selectCacheData(tendency);
            if (primaryCacheData == null) {
                return;
            }
            boolean remove = primaryCacheData.updateAfterMatchNotPassed(vectorClient);
            if (remove) {
                toRemove.add(primaryCacheData);
            }

        });
        cacheLock.lock();
        actionCache.removeAll(toRemove);
        cacheLock.unlock();
    }

    /**
     * 针对未命中但评估通过的缓存做出调整:
     * <ol>
     * <h3>如果存在缓存条目</h3>
     * <li>
     * 若已生效，但此时未匹配到则说明尚未生效或者阈值、向量{@link ActionCacheData#getInputVector()}存在问题，调低阈值，同时带权移动平均
     * </li>
     * <li>
     * 若未生效，则只增加计数并带权移动平均
     * </li>
     * </ol>
     * 如果不存在缓存条目，则新增并填充字段
     *
     * @param notMatchPassed 该类型的带调整缓存信息列表
     * @param inputVector    本次输入内容的语义向量
     * @param input          本次输入内容
     * @param vectorClient   向量客户端
     */
    private void adjustNotMatchPassed(List<CacheAdjustMetaData> notMatchPassed, float[] inputVector, String input,
                                      VectorClient vectorClient) {
        notMatchPassed.forEach(adjustData -> {
            // 获取原始缓存条目
            String tendency = adjustData.getTendency();
            ActionCacheData primaryCacheData = selectCacheData(tendency);
            float[] tendencyVector = vectorClient.compute(tendency);
            if (primaryCacheData == null) {
                actionCache.add(new ActionCacheData(tendency, tendencyVector, inputVector, input));
                return;
            }
            primaryCacheData.updateAfterNotMatchPassed(input, inputVector, tendencyVector, vectorClient);
        });
    }

    private ActionCacheData selectCacheData(String tendency) {
        for (ActionCacheData actionCacheData : actionCache) {
            if (actionCacheData.getTendency().equals(tendency)) {
                return actionCacheData;
            }
        }
        log.warn("[{}] 未找到行为倾向[{}]对应的缓存条目，可能是代码逻辑存在错误", getCoreKey(), tendency);
        return null;
    }

    @Override
    protected String getCoreKey() {
        return "action-core";
    }

    public enum ExecutorType {
        VIRTUAL, PLATFORM
    }

}
