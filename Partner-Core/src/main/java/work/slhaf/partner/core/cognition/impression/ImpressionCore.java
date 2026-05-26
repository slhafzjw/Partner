package work.slhaf.partner.core.cognition.impression;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.framework.agent.state.State;
import work.slhaf.partner.framework.agent.state.StateSerializable;
import work.slhaf.partner.framework.agent.state.StateValue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@CapabilityCore(value = "cognition")
public class ImpressionCore implements StateSerializable {

    /**
     * Keyed by entity uuid. Subject can be revised or merged later, so it should not be used as the stable key.
     */
    private final ConcurrentHashMap<String, Entity> knownEntitiesByUuid = new ConcurrentHashMap<>();

    @CapabilityMethod
    public void updateRelation() {
    }

    @CapabilityMethod
    public void updateImpression() {
    }

    @CapabilityMethod
    public void showImpressions() {
    }

    @CapabilityMethod
    public void projectEntity(Set<ActiveEntity> activeEntities) {
    }

    @Override
    public @NotNull Path statePath() {
        return Path.of("core", "impression.json");
    }

    @Override
    public void load(@NotNull JSONObject state) {
        JSONArray entityArray = state.getJSONArray("entities");
        if (entityArray == null) {
            return;
        }

        knownEntitiesByUuid.clear();
        for (int i = 0; i < entityArray.size(); i++) {
            JSONObject entityObject = entityArray.getJSONObject(i);
            if (entityObject == null) {
                continue;
            }

            String uuid = entityObject.getString("uuid");
            String subject = entityObject.getString("subject");
            if (uuid == null || uuid.isBlank() || subject == null || subject.isBlank()) {
                continue;
            }

            Entity entity = new Entity(uuid, subject);
            entity.load();
            knownEntitiesByUuid.put(uuid, entity);
        }
    }


    @Override
    public @NotNull State convert() {
        State state = new State();

        List<StateValue.Obj> entities = knownEntitiesByUuid.values().stream()
                .map(entity -> StateValue.obj(Map.of(
                        "uuid", entity.getUuid(),
                        "subject", entity.getSubject()
                )))
                .toList();

        state.append("entities", StateValue.arr(entities));
        return state;
    }
}
