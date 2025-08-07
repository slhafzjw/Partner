package work.slhaf.partner.api.agent.flow.entity;

import lombok.Data;
import work.slhaf.partner.api.chat.ChatClient;
import work.slhaf.partner.api.chat.pojo.Message;

import java.util.List;

@Data
public class Model {

    protected ChatClient chatClient;
    protected List<Message> chatMessages;
    protected List<Message> baseMessages;

}
