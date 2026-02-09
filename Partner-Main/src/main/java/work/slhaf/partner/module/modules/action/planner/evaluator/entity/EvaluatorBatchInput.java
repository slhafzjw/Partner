package work.slhaf.partner.module.modules.action.planner.evaluator.entity;

import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.memory.pojo.EvaluatedSlice;

import java.util.List;
import java.util.Map;

@Data
public class EvaluatorBatchInput {
    private List<Message> recentMessages;
    private List<EvaluatedSlice> activatedSlices;
    private Map<String, String> availableActions;
    private String tendency;
}
