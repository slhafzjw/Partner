package work.slhaf.partner.core.action;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.core.action.entity.cache.CacheAdjustData;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

@Capability(value = "action")
public interface ActionCapability {
    void putPreparedAction(String uuid, ActionData actionData);

    List<ActionData> popPendingAction(String userId);

    List<ActionData> listPreparedAction(String userId);

    List<ActionData> listPendingAction(String userId);

    void putPendingActions(String userId, ActionData actionData);

    List<String> selectTendencyCache(String input);

    void updateTendencyCache(CacheAdjustData data);

    ExecutorService getExecutor(ActionCore.ExecutorType type);

    Set<String> getExistedMetaActions();
}
