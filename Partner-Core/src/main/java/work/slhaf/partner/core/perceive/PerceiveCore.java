package work.slhaf.partner.core.perceive;

import com.alibaba.fastjson2.JSONObject;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityMethod;
import work.slhaf.partner.framework.agent.state.State;
import work.slhaf.partner.framework.agent.state.StateSerializable;
import work.slhaf.partner.framework.agent.state.StateValue;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@CapabilityCore(value = "perceive")
public class PerceiveCore implements StateSerializable {

    private Instant lastInteractTime = Instant.ofEpochMilli(0);

    public PerceiveCore() {
        register();
    }

    @CapabilityMethod
    public String refreshInteract() {
        String last = lastInteractTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        lastInteractTime = Instant.now();
        return last;
    }

    @CapabilityMethod
    public Instant showLastInteract() {
        return lastInteractTime;
    }

    @Override
    public @NotNull Path statePath() {
        return Path.of("core", "perceive.json");
    }

    @Override
    public void load(@NotNull JSONObject state) {
        this.lastInteractTime = Instant.ofEpochMilli(state.getLong("last_interact_time"));
    }

    @Override
    public @NotNull State convert() {
        State state = new State();
        state.append("last_interact_time", StateValue.num(lastInteractTime.toEpochMilli()));
        return state;
    }
}
