package work.slhaf.partner.core.cognition.impression;

import com.alibaba.fastjson2.JSONObject;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.framework.agent.state.State;
import work.slhaf.partner.framework.agent.state.StateSerializable;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@CapabilityCore(value = "cognition")
public class ImpressionCore implements StateSerializable {

    private final ConcurrentHashMap<String, Entity> knownEntities = new ConcurrentHashMap<>();

    @CapabilityMethod
    public void updateRelation(){
    }

    @CapabilityMethod
    public void updateImpression(){
    }

    @CapabilityMethod
    public void showImpressions(){
    }

    @CapabilityMethod
    public void projectEntity(Set<ActiveEntity> activeEntities){
    }

    @Override
    public @NotNull Path statePath() {
        return Path.of("core", "impression.json");
    }

    @Override
    public void load(@NotNull JSONObject state) {

    }


    @Override
    public @NotNull State convert() {
        State state = new State();
        return state;
    }
}
