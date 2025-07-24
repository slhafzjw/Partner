package work.slhaf.partner.api.flow.abstracts;

import lombok.Data;
import work.slhaf.partner.api.common.chat.ChatClient;
import work.slhaf.partner.api.common.chat.pojo.Message;

import java.util.List;

@Data
public class Model {

    protected ChatClient chatClient;
    protected List<Message> chatMessages;
    protected List<Message> baseMessages;

}
