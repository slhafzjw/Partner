package work.slhaf.partner.module.modules.action.planner.evaluator.entity;

import lombok.Data;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.core.action.entity.ActionType;

import java.time.LocalDateTime;

@Data
public class EvaluatorResult {
    private boolean ok;
    private ActionType type;
    private String scheduleContent;
    private ActionData actionData;
}
