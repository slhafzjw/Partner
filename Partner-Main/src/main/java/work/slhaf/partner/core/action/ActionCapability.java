package work.slhaf.partner.core.action;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

import java.util.List;

@Capability(value = "action")
public interface ActionCapability {
    void putPreparedAction(String uuid, MetaActionInfo metaActionInfo);

    List<MetaActionInfo> popPreparedAction(String userId);

    List<MetaActionInfo> popPendingAction(String userId);

    List<MetaActionInfo> listPreparedAction(String userId);

    List<MetaActionInfo> listPendingAction(String userId);

    void putPendingActions(String userId, MetaActionInfo metaActionInfo);
}
