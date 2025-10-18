package work.slhaf.partner.module.modules.action.planner.evaluator.entity;

import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.memory.pojo.EvaluatedSlice;

import java.util.List;

@Data
public class EvaluatorBatchInput {
    private List<Message> recentMessages;
    private List<EvaluatedSlice> activatedSlices;
    private String tendency;
}
