package work.slhaf.partner.module.modules.action.identifier.evaluator.entity;

import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.memory.pojo.EvaluatedSlice;

import java.util.List;

@Data
public class EvaluatorInput {
    private String interventionTendency;
    private List<EvaluatedSlice> activatedSlices;
    private List<Message> recentMessages;
}
