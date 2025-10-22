package work.slhaf.partner.core.action.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ScheduledActionInfo extends ActionInfo {
    private ScheduledType type;
    private String scheduleContent; //如果为周期，则对应cron表达式，如果为一次性，则对应为LocalDateTime字符串

    enum ScheduledType {
        CYCLE, ONCE
    }
}
