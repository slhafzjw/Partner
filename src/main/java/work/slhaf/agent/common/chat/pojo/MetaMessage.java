package work.slhaf.agent.common.chat.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MetaMessage {
    private Message userMessage;
    private Message assistantMessage;
}
