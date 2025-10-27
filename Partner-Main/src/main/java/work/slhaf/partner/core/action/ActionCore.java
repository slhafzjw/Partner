package work.slhaf.partner.core.action;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.common.vector.VectorClient;
import work.slhaf.partner.core.PartnerCore;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.entity.cache.ActionCacheData;
import work.slhaf.partner.core.action.entity.cache.CacheAdjustData;
import work.slhaf.partner.core.action.entity.cache.CacheAdjustMetaData;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private HashMap<String, List<ActionData>> actionPool = new HashMap<>();

    /**
     * 待确认任务，以userId区分不同用户，因为需要跨请求确认
     */
    private HashMap<String, List<ActionData>> pendingActions = new HashMap<>();

    /**
     * 语义缓存与行为倾向映射
     */
    private List<ActionCacheData> actionCache = new ArrayList<>();

    private final Lock cacheLock = new ReentrantLock();

    private final ExecutorService platformExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final LinkedHashMap<String, MetaActionInfo> existedMetaActions = new LinkedHashMap<>();

    public ActionCore() throws IOException, ClassNotFoundException {
        new ActionWatchService(existedMetaActions, virtualExecutor).launch();
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
        //计算本次输入的向量
        float[] vector = vectorClient.compute(input);
        if (vector == null) return null;
        //与现有缓存比对，将匹配到的收集并返回
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
    public Set<String> getExistedMetaActions() {
        return existedMetaActions.keySet();
    }

    /**
     * 命中缓存且评估通过时
     *
     * @param matchAndPassed 该类型的带调整缓存信息列表
     * @param inputVector    本次输入内容的语义向量
     * @param vectorClient   向量客户端
     */
    private void adjustMatchAndPassed(List<CacheAdjustMetaData> matchAndPassed, float[] inputVector, String
            input, VectorClient vectorClient) {
        matchAndPassed.forEach(adjustData -> {
            //获取原始缓存条目
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
            //获取原始缓存条目
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
     *     <h3>如果存在缓存条目</h3>
     *     <li>
     *         若已生效，但此时未匹配到则说明尚未生效或者阈值、向量{@link ActionCacheData#getInputVector()}存在问题，调低阈值，同时带权移动平均
     *     </li>
     *     <li>
     *         若未生效，则只增加计数并带权移动平均
     *     </li>
     * </ol>
     * 如果不存在缓存条目，则新增并填充字段
     *
     * @param notMatchPassed 该类型的带调整缓存信息列表
     * @param inputVector    本次输入内容的语义向量
     * @param input          本次输入内容
     * @param vectorClient   向量客户端
     */
    private void adjustNotMatchPassed(List<CacheAdjustMetaData> notMatchPassed, float[] inputVector, String
            input, VectorClient vectorClient) {
        notMatchPassed.forEach(adjustData -> {
            //获取原始缓存条目
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
