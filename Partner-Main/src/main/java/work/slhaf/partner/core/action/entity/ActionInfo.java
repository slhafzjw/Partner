package work.slhaf.partner.core.action.entity;

import lombok.Data;

import java.util.List;

@Data
public abstract class ActionInfo {
    protected String uuid;
    protected String tendency;
    protected ActionStatus status;
    protected List<MetaAction> actionChain;
    protected String Result;
    protected String reason;
    protected String description;
}
