package work.slhaf.partner.core.interaction.data.context.subcontext;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.common.entity.PersistableObject;

import java.io.Serial;
import java.util.HashMap;

@EqualsAndHashCode(callSuper = true)
@Data
public class CoreContext extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

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
