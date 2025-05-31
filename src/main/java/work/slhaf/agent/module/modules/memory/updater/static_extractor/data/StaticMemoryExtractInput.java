package work.slhaf.agent.module.modules.memory.updater.static_extractor.data;

import lombok.Builder;
import lombok.Data;
import work.slhaf.agent.common.chat.pojo.Message;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class StaticMemoryExtractInput {
    private String userId;
    private List<Message> messages;
    private Map<String,String> existedStaticMemory;
}
