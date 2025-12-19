package work.slhaf.partner.core.action.entity;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MetaActionInfo {
    private boolean io;
    private MetaActionType type;

    private Map<String, String> params;
    private String description;
    private List<String> tags;

    private List<String> preActions;
    private List<String> postActions;
    /**
     * 是否严格依赖前置行动的成功执行，若为true且前置行动失败则不执行该行动，后置任务多为触发式。默认即执行。
     */
    private boolean strictDependencies;

    private JSONObject responseSchema;
}
