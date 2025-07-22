package work.slhaf.partner.module.common.model;

import lombok.Data;
import work.slhaf.partner.common.chat.ChatClient;
import work.slhaf.partner.common.chat.pojo.Message;

import java.util.List;

@Data
public class Model {

    protected ChatClient chatClient;
    protected List<Message> chatMessages;
    protected List<Message> baseMessages;

}
