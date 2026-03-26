package work.slhaf.partner.module.action.planner.evaluator.entity;

import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.memory.pojo.ActivatedMemorySlice;

import java.util.List;
import java.util.Map;

@Data
public class EvaluatorBatchInput {
    private List<Message> recentMessages;
    private List<ActivatedMemorySlice> activatedSlices;
    private Map<String, String> availableActions;
    private String tendency;
}
