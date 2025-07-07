package work.slhaf.agent.module.modules.perceive.updater.relation_extractor.pojo;

import lombok.Data;
import work.slhaf.agent.common.chat.pojo.Message;

import java.util.HashMap;
import java.util.List;

@Data
public class RelationExtractInput {
    private HashMap<String,String> primaryUserPerceive;
    private List<Message> chatMessages;
}
