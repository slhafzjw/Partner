package work.slhaf.agent.core.interaction.data;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.pojo.PersistableObject;
import work.slhaf.agent.module.common.AppendPromptData;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    protected String input;

    protected JSONObject coreContext;
    protected JSONObject moduleContext;
    protected JSONObject coreResponse;

    public InteractionContext() {
        currentContext = this;
        moduleContext.put(Constant.APPENDED_PROMPT,new JSONArray());
    }

    public void setFinished(boolean finished) {
        moduleContext.put(Constant.FINISHED,finished);
    }

    public boolean isFinished(){
        return moduleContext.getBooleanValue(Constant.FINISHED);
    }

    public void setAppendedPrompt(AppendPromptData appendedPrompt){
        moduleContext.getJSONArray(Constant.APPENDED_PROMPT).add(appendedPrompt);
    }

    public List<AppendPromptData> getAppendedPrompt(){
        List<AppendPromptData> list = new ArrayList<>();
        for (Object o : moduleContext.getJSONArray(Constant.APPENDED_PROMPT)) {
            JSONObject object = (JSONObject) o;
            list.add(object.to(AppendPromptData.class));
        }
        return list;
    }

    public static InteractionContext getInstance() {
        return currentContext;
    }

    public static void clearUp(){
        currentContext = null;
    }

    private static class Constant{
        private static final String FINISHED = "finished";
        private static final String APPENDED_PROMPT = "appendedPrompt";
    }

}
