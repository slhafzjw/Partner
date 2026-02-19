package work.slhaf.partner.runtime.interaction.data.context.subcontext;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.common.entity.PersistableObject;
import work.slhaf.partner.module.common.entity.AppendPromptData;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ModuleContext extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<AppendPromptData> appendedPrompt = new ArrayList<>();
    private JSONObject extraContext = new JSONObject();
    private boolean finished = false;
}
