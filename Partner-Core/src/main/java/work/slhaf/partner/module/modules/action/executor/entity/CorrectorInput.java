package work.slhaf.partner.module.modules.action.executor.entity;

import lombok.Builder;
import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.action.entity.ExecutableAction;
import work.slhaf.partner.core.memory.pojo.ActivatedMemorySlice;

import java.util.List;

@Data
@Builder
public class CorrectorInput {
    private String tendency;
    private String source;
    private String reason;
    private String description;

    private List<HistoryAction> history;
    private ExecutableAction.Status status;

    private List<Message> recentMessages;
    private List<ActivatedMemorySlice> activatedSlices;
}
