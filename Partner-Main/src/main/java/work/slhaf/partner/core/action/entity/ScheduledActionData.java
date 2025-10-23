package work.slhaf.partner.core.action.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 计划行动数据类，继承自{@link ActionData}，扩展了属性{@link ScheduledActionData#type}和{@link ScheduledActionData#scheduleContent}，用于标识计划类型(单次还是周期性任务)和计划内容
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ScheduledActionData extends ActionData {
    private ScheduledType type;
    private String scheduleContent; //如果为周期，则对应cron表达式，如果为一次性，则对应为LocalDateTime字符串

    enum ScheduledType {
        CYCLE, ONCE
    }
}
