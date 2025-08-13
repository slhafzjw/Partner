package work.slhaf.partner.api.agent.runtime.interaction.data;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public abstract class InteractionData {
    protected String userInfo;
    protected String content;
    protected LocalDateTime dateTime;
}
