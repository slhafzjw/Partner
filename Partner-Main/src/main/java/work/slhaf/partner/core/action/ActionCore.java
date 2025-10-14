package work.slhaf.partner.core.action;

import lombok.Getter;
import lombok.Setter;
import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.core.PartnerCore;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Setter
@Getter
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

    //TODO 添加语义缓存，可借由简单向量模型，设想以向量结果为键、行动倾向为值
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

    @Override
    protected String getCoreKey() {
        return "action-core";
    }
}
