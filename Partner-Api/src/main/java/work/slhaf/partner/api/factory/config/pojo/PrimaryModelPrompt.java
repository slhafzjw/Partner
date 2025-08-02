package work.slhaf.partner.api.factory.config.pojo;

import lombok.Data;
import work.slhaf.partner.api.common.chat.pojo.Message;

import java.util.List;

@Data
public class PrimaryModelPrompt {
    private String key;
    private List<Message> messages;
}
