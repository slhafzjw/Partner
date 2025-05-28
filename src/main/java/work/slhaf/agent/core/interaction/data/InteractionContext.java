package work.slhaf.agent.core.interaction.data;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.pojo.PersistableObject;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class InteractionContext extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private static InteractionContext currentContext;

    protected String userId;
    protected String userNickname;
    protected String userInfo;
    protected LocalDateTime dateTime;
    protected boolean single;

    protected boolean finished;
    protected String input;

    protected JSONObject coreContext;
    protected JSONObject moduleContext;
    protected List<String> appendPrompt;
    protected JSONObject coreResponse;

    public InteractionContext() {
        currentContext = this;
    }

    public static InteractionContext getInstance() {
        return currentContext;
    }

    public static void clearUp(){
        currentContext = null;
    }
}
