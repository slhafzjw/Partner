package work.slhaf.agent.common.chat.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.pojo.PersistableObject;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
public class MetaMessage extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private Message userMessage;
    private Message assistantMessage;
}
