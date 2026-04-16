package work.slhaf.partner.module.action.planner.evaluator.entity;

import lombok.Data;
import work.slhaf.partner.core.action.entity.Schedulable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class EvaluatorResult {
    private boolean ok;
    private boolean needConfirm;
    private ResolvedPending resolvedPending;
    private ActionType type;
    private ScheduleData scheduleData;
    private List<ChainElement> primaryActionChain;
    private String tendency;
    private String reason;
    private String description;

    public enum ActionType {
        IMMEDIATE, PLANNING
    }

    @Data
    public static class ResolvedPending {
        private String blockName;
        private String source;
    }

    public Map<Integer, List<String>> getPrimaryActionChainAsMap() {
        return primaryActionChain.stream().collect(Collectors.toMap(
                ChainElement::getOrder,
                ChainElement::getActionKeys,
                (oldValue, newValue) -> newValue,
                LinkedHashMap::new
        ));
    }

    @Data
    public static class ScheduleData {
        private String content;
        private Schedulable.ScheduleType type;
    }

    @Data
    public static class ChainElement {
        private Integer order;
        private List<String> actionKeys;
    }
}
