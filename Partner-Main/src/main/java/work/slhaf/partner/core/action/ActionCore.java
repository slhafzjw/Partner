package work.slhaf.partner.core.action;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.common.vector.VectorClient;
import work.slhaf.partner.core.PartnerCore;
import work.slhaf.partner.core.action.entity.ActionCacheData;
import work.slhaf.partner.core.action.entity.CacheAdjustData;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("FieldMayBeFinal")
@Capability(value = "action")
public class ActionCore extends PartnerCore<ActionCore> {

    /**
     * 对应本次交互即将执行或将要放置在行动池的预备任务，因此将以本次交互的uuid为键，其起到的作用相当于暂时的模块上下文
     */
    private HashMap<String, List<MetaActionInfo>> preparedActions = new HashMap<>();

    /**
     * 待确认任务，以userId区分不同用户，因为需要跨请求确认
     */
    private HashMap<String, List<MetaActionInfo>> pendingActions = new HashMap<>();

    /**
     * 语义缓存与行为倾向映射
     */
    private List<ActionCacheData> actionCache = new ArrayList<>();

    public ActionCore() throws IOException, ClassNotFoundException {
    }

    @CapabilityMethod
    public synchronized void putPendingActions(String userId, MetaActionInfo metaActionInfo) {
        pendingActions.computeIfAbsent(userId, k -> {
            List<MetaActionInfo> temp = new ArrayList<>();
            temp.add(metaActionInfo);
            return temp;
        });
    }

    @CapabilityMethod
    public synchronized List<MetaActionInfo> popPendingAction(String userId) {
        List<MetaActionInfo> infos = pendingActions.get(userId);
        pendingActions.remove(userId);
        return infos;
    }

    @CapabilityMethod
    public synchronized void putPreparedAction(String uuid, MetaActionInfo metaActionInfo) {
        preparedActions.computeIfAbsent(uuid, k -> {
            List<MetaActionInfo> temp = new ArrayList<>();
            temp.add(metaActionInfo);
            return temp;
        });
    }

    @CapabilityMethod
    public synchronized List<MetaActionInfo> popPreparedAction(String userId) {
        List<MetaActionInfo> infos = preparedActions.get(userId);
        preparedActions.remove(userId);
        return infos;
    }

    @CapabilityMethod
    public List<MetaActionInfo> listPreparedAction(String userId) {
        return preparedActions.get(userId);
    }

    @CapabilityMethod
    public List<MetaActionInfo> listPendingAction(String userId) {
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
    public void updateTendencyCache(List<CacheAdjustData> list) {
        List<CacheAdjustData> matchAndPassed = new ArrayList<>();
        List<CacheAdjustData> matchNotPassed = new ArrayList<>();
        List<CacheAdjustData> notMatchPassed = new ArrayList<>();

        for (CacheAdjustData data : list) {
            if (data.isHit() && data.isPassed()) {
                matchAndPassed.add(data);
            } else if (data.isHit()) {
                matchNotPassed.add(data);
            } else if (!data.isPassed()) {
                notMatchPassed.add(data);
            }
        }

        VectorClient vectorClient = VectorClient.INSTANCE;
        adjustMatchAndPassed(matchAndPassed, vectorClient);
        adjustMatchNotPassed(matchNotPassed, vectorClient);
        adjustNotMatchPassed(notMatchPassed, vectorClient);
    }

    /**
     * 命中缓存且评估通过时，根据输入内容的语义向量与现有的输入语义向量进行带权移动平均，以相似度为权重
     *
     * @param matchAndPassed 该类型的带调整缓存信息列表
     * @param vectorClient   向量客户端
     */
    private void adjustMatchAndPassed(List<CacheAdjustData> matchAndPassed, VectorClient vectorClient) {

    }

    private void adjustMatchNotPassed(List<CacheAdjustData> matchNotPassed, VectorClient vectorClient) {

    }

    private void adjustNotMatchPassed(List<CacheAdjustData> notMatchPassed, VectorClient vectorClient) {

    }

    @Override
    protected String getCoreKey() {
        return "action-core";
    }
}
