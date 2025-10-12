package work.slhaf.partner.module.modules.action.planner.evaluator.entity;

import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.memory.pojo.EvaluatedSlice;
import work.slhaf.partner.core.perceive.pojo.User;

import java.util.List;

@Data
public class EvaluatorInput {
    private List<Message> recentMessages;
    private User user;
    private List<EvaluatedSlice> activatedSlices;
    private String tendency;
}
