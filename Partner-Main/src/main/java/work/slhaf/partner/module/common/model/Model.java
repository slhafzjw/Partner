package work.slhaf.partner.module.common.model;

import lombok.Data;
import work.slhaf.partner.common.chat.ChatClient;
import work.slhaf.partner.common.chat.constant.ChatConstant;
import work.slhaf.partner.common.chat.pojo.ChatResponse;
import work.slhaf.partner.common.chat.pojo.Message;
import work.slhaf.partner.common.config.ModelConfig;
import work.slhaf.partner.common.util.ResourcesUtil;

import java.util.ArrayList;
import java.util.List;

@Data
public abstract class Model {

    protected ChatClient chatClient;
    protected List<Message> chatMessages;
    protected List<Message> baseMessages;

}
