package work.slhaf.partner.module.modules.perceive.updater.static_extractor.data;

import lombok.Builder;
import lombok.Data;
import work.slhaf.partner.common.chat.pojo.Message;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class StaticMemoryExtractInput {
    private String userId;
    private List<Message> messages;
    private Map<String,String> existedStaticMap;
}
