package work.slhaf.partner.core.perceive;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import work.slhaf.partner.core.PartnerCore;
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityCore;
import work.slhaf.partner.framework.agent.factory.capability.annotation.CapabilityMethod;

import java.io.IOException;
import java.io.Serial;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@CapabilityCore(value = "perceive")
@Getter
@Setter
public class PerceiveCore extends PartnerCore<PerceiveCore> {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final ReentrantLock usersLock = new ReentrantLock();

    private Instant lastInteractTime;

    public PerceiveCore() throws IOException, ClassNotFoundException {
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
    protected String getCoreKey() {
        return "perceive-core";
    }
}
