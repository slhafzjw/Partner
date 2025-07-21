package work.slhaf.partner.module.common.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.common.serialize.PersistableObject;

import java.io.Serial;
import java.util.HashMap;

@EqualsAndHashCode(callSuper = true)
@Data
public class AppendPromptData extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private String moduleName;
    private HashMap<String,String> appendedPrompt;
}
