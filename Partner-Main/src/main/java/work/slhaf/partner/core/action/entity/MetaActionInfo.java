package work.slhaf.partner.core.action.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MetaActionInfo {
    private ActionData actionData;
    private ActionStatus status;
    private String Result;
    private LocalDateTime dateTime;
}
