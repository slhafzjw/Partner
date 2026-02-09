package work.slhaf.partner.module.modules.action.planner.confirmer.entity;

import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.action.entity.ActionData;

import java.util.List;

@Data
public class ConfirmerInput {
    private String input;
    private List<ActionData> actionData;
    private List<Message> recentMessages;
}
