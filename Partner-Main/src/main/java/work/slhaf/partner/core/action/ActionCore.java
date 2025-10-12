package work.slhaf.partner.core.action;

import lombok.Getter;
import lombok.Setter;
import work.slhaf.partner.api.agent.factory.capability.annotation.Capability;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.core.PartnerCore;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

import java.io.IOException;
import java.util.HashMap;

@Setter
@Getter
@Capability(value = "action")
public class ActionCore extends PartnerCore<ActionCore> {

    private HashMap<String, MetaActionInfo> preparedActions = new HashMap<>();

    public ActionCore() throws IOException, ClassNotFoundException {
    }

    @CapabilityMethod
    public synchronized void putPreparedAction(String uuid, MetaActionInfo metaActionInfo) {
        preparedActions.put(uuid, metaActionInfo);
    }

    @CapabilityMethod
    public MetaActionInfo getPreparedAction(String userId){
        return preparedActions.get(userId);
    }

    @Override
    protected String getCoreKey() {
        return "action-core";
    }
}
