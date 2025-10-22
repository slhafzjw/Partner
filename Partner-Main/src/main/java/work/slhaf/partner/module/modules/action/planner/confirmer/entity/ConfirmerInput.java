package work.slhaf.partner.module.modules.action.planner.confirmer.entity;

import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.action.entity.ActionInfo;

import java.util.List;

@Data
public class ConfirmerInput {
    private String input;
    private List<ActionInfo> actionInfos;
    private List<Message> recentMessages;
}
