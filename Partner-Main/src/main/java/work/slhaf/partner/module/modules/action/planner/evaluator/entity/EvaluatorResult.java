package work.slhaf.partner.module.modules.action.planner.evaluator.entity;

import lombok.Data;
import work.slhaf.partner.core.action.entity.SchedulableExecutableAction;

import java.util.List;
import java.util.Map;

@Data
public class EvaluatorResult {
    private boolean ok;
    private boolean needConfirm;
    private ActionType type;
    private String scheduleContent;
    private SchedulableExecutableAction.ScheduleType scheduleType;
    private Map<Integer, List<String>> primaryActionChain;
    private String tendency;
    private String reason;
    private String description;

    public enum ActionType {
        IMMEDIATE, PLANNING
    }
}
