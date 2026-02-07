package work.slhaf.partner.core.action.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.HistoryAction;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 计划行动数据类，继承自{@link ActionData}，扩展了属性{@link ScheduledActionData#type}和{@link ScheduledActionData#scheduleContent}，用于标识计划类型(单次还是周期性任务)和计划内容
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ScheduledActionData extends ActionData {
    private ScheduledType type;
    private String scheduleContent; //如果为周期，则对应cron表达式，如果为一次性，则对应为ZonedDateTime字符串
    private List<ScheduledHistory> scheduledHistories = new ArrayList<>();

    public void recordAndReset() {
        this.scheduledHistories.add(new ScheduledHistory(ZonedDateTime.now(), this.result, Map.copyOf(this.history)));

        // 清理执行时内容
        this.additionalContext.clear();
        this.executingStage = 0;
        this.getActionChain().forEach((stage, actions) -> {
            for (MetaAction action : actions) {
                action.getParams().clear();
                action.setResult(new MetaAction.Result());
            }
        });

        this.setStatus(ActionStatus.PREPARE);
    }

    public enum ScheduledType {
        CYCLE, ONCE
    }

    private record ScheduledHistory(ZonedDateTime endTime, String result, Map<Integer, List<HistoryAction>> history) {
    }
}
