package work.slhaf.partner.module.modules.action.planner.evaluator.entity;

import lombok.Data;

import java.util.List;

@Data
public class EvaluatorInput {
    private List<String> recentMessages;
    private String tendency;
}
