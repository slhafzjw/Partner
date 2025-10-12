package work.slhaf.partner.core.action;

import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

@Capability(value = "action")
public interface ActionCapability {
    void putPreparedAction(String uuid, MetaActionInfo metaActionInfo);

    MetaActionInfo getPreparedAction(String userId);
}
