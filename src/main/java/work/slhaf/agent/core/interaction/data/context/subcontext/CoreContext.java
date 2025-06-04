package work.slhaf.agent.core.interaction.data.context.subcontext;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.HashMap;

@Data
public class CoreContext {
    private String text;
    private String dateTime;
    private String userNick;
    private String userId;
    private HashMap<String, Boolean> activeModules = new HashMap<>();

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }

    public void addActiveModule(String moduleName) {
        activeModules.put(moduleName, false);
    }

    public void activateModule(String moduleName){
        activeModules.put(moduleName, true);
    }
}
