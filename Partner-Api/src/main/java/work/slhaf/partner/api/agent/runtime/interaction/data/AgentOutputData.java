package work.slhaf.partner.api.agent.runtime.interaction.data;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public abstract class AgentOutputData extends InteractionData{

    private int code;

    public static class StatusCode {
        public static final int SUCCESS = 1;
        public static final int FAILED = 0;
    }
}
