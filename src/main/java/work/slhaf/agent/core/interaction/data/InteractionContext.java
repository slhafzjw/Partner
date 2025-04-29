package work.slhaf.agent.core.interaction.data;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InteractionContext {
    protected String userId;
    protected String userNickname;
    protected String userInfo;
    protected LocalDateTime dateTime;
    protected boolean single;

    protected boolean finished;
    protected String input;

    protected JSONObject coreContext;
    protected JSONObject moduleContext;
    protected JSONObject modulePrompt;
    protected JSONObject coreResponse;
}
