package work.slhaf.partner.module.modules.action.planner.extractor.entity;

import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;

import java.util.List;

@Data
public class ExtractorInput {
    private String input;
    private List<Message> recentMessages;
}
