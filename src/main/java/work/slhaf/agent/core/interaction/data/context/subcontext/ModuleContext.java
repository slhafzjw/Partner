package work.slhaf.agent.core.interaction.data.context.subcontext;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import work.slhaf.agent.module.common.AppendPromptData;

import java.util.ArrayList;
import java.util.List;

@Data
public class ModuleContext {
    private List<AppendPromptData> appendedPrompt = new ArrayList<>();
    private JSONObject extraContext = new JSONObject();
    private boolean finished = false;
}
