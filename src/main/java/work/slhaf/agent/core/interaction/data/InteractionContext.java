package work.slhaf.agent.core.interaction.data;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InteractionContext {
    protected String userInfo;
    protected String userNickname;
    protected LocalDateTime dateTime;

    protected boolean finished;
    protected String input;

    protected JSONObject moduleContext;
    protected JSONObject coreResponse;
}
