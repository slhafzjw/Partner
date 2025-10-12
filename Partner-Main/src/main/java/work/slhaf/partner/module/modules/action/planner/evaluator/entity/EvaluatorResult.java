package work.slhaf.partner.module.modules.action.planner.evaluator.entity;

import lombok.Data;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.core.action.entity.ActionType;

@Data
public class EvaluatorResult {
    private boolean ok;
    private ActionType type;
    private String typeInfo;
    private ActionData actionData;
}
