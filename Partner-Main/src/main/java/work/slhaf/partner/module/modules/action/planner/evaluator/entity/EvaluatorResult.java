package work.slhaf.partner.module.modules.action.planner.evaluator.entity;

import lombok.Data;
import work.slhaf.partner.core.action.entity.ActionType;
import work.slhaf.partner.core.action.entity.MetaAction;

import java.util.List;

@Data
public class EvaluatorResult {
    private boolean ok;
    private boolean needConfirm;
    private ActionType type;
    private String scheduleContent;
    private List<MetaAction> actionChain;
    private String tendency;
}
