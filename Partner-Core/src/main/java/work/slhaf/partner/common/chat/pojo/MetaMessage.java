package work.slhaf.partner.common.chat.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.common.serialize.PersistableObject;

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
