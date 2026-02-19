package work.slhaf.partner.module.modules.action.dispatcher.executor.entity;

import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;

import java.util.List;
import java.util.Map;

@Data
public class RepairerInput {

    private String userId;
    private List<Message> recentMessages;
    private Map<String, Object> params;
    private String actionDescription;
    private List<HistoryAction> historyActionResults;
}
