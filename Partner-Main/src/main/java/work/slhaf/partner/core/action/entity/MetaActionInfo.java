package work.slhaf.partner.core.action.entity;

import lombok.Data;

@Data
public abstract class MetaActionInfo {
    protected String uuid;
    protected String tendency;
    protected ActionStatus status;
    protected ActionData actionData;
    protected String Result;
}
