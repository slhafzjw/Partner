package work.slhaf.partner.module.modules.action.interventor.evaluator.entity;

import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.core.memory.pojo.EvaluatedSlice;

import java.util.List;
import java.util.Map;

@Data
public class EvaluatorInput {
    private Map<String, ActionData> executingInterventions;
    private Map<String, ActionData> preparedInterventions;
    private List<EvaluatedSlice> activatedSlices;
    private List<Message> recentMessages;
}
