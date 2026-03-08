package work.slhaf.partner.core.action.entity;

import lombok.Data;

import java.util.UUID;

@Data
public class PendingActionRecord {

    private final String pendingId = UUID.randomUUID().toString();
    private String userId;
    private ExecutableAction executableAction;
    private Status status = Status.WAITING_CONFIRM;

    private long createdAt;
    private long remindAt;
    private long expireAt;

    private boolean reminded;
    private long decisionAt;
    private String decisionReason;

    private String reminderActionId;
    private String expireActionId;

    public enum Status {
        WAITING_CONFIRM,
        REMINDER_SENT,
        CONFIRMED,
        REJECTED,
        EXPIRED
    }

    public enum Decision {
        CONFIRM,
        REJECT,
        HOLD
    }
}
