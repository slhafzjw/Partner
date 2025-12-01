package work.slhaf.partner.module.modules.action.planner.evaluator.entity;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class EvaluatorResult {
    private boolean ok;
    private boolean needConfirm;
    private ActionType type;
    private String scheduleContent;
    private Map<Integer, List<String>> primaryActionChain;
    private String tendency;

    public enum ActionType {
        IMMEDIATE, PLANNING
    }
}
