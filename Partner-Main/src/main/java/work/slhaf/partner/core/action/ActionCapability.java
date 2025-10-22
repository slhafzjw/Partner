package work.slhaf.partner.core.action;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.core.action.entity.ActionInfo;
import work.slhaf.partner.core.action.entity.cache.CacheAdjustData;

import java.util.List;

@Capability(value = "action")
public interface ActionCapability {
    void putPreparedAction(String uuid, ActionInfo actionInfo);

    List<ActionInfo> popPreparedAction(String userId);

    List<ActionInfo> popPendingAction(String userId);

    List<ActionInfo> listPreparedAction(String userId);

    List<ActionInfo> listPendingAction(String userId);

    void putPendingActions(String userId, ActionInfo actionInfo);

    List<String> selectTendencyCache(String input);

    void updateTendencyCache(CacheAdjustData data);
}
