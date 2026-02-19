package work.slhaf.partner.api.agent.factory.config.pojo;

import lombok.Data;
import work.slhaf.partner.api.chat.pojo.Message;

import java.util.List;

@Data
public class PrimaryModelPrompt {
    private String key;
    private List<Message> messages;
}
