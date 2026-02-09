package work.slhaf.partner.module.common.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.common.entity.PersistableObject;

import java.io.Serial;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class AppendPromptData extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private String moduleName;
    private Map<String, String> appendedPrompt;
}
