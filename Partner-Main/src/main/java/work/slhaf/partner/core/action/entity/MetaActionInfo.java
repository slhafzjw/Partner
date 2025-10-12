package work.slhaf.partner.core.action.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public abstract class MetaActionInfo {
    protected String tendency;
    protected ActionStatus status;
    protected ActionData actionData;
    protected String Result;
}
