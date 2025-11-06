package work.slhaf.partner.module.modules.action.identifier.evaluator.entity;

import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.memory.pojo.EvaluatedSlice;

import java.util.List;
import java.util.Set;

@Data
public class EvaluatorInput {
    private Set<String> interventionTendencies;
    private List<EvaluatedSlice> activatedSlices;
    private List<Message> recentMessages;
}
