package work.slhaf.partner.core.interaction.data.context;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.common.serialize.PersistableObject;
import work.slhaf.partner.core.interaction.data.context.subcontext.CoreContext;
import work.slhaf.partner.core.interaction.data.context.subcontext.ModuleContext;
import work.slhaf.partner.module.common.AppendPromptData;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class InteractionContext extends PersistableObject {

    @Serial
    private static final long serialVersionUID = 1L;

    private static HashMap<String, InteractionContext> activeContext = new HashMap<>();

    protected String userId;
    protected String userNickname;
    protected String userInfo;
    protected LocalDateTime dateTime;
    protected boolean single;

    protected String input;

    protected CoreContext coreContext = new CoreContext();
    protected ModuleContext moduleContext = new ModuleContext();
    protected JSONObject coreResponse = new JSONObject();

    public InteractionContext() {
        activeContext.put(userId, this);
    }

    public void setFinished(boolean finished) {
        moduleContext.setFinished(finished);
    }

    public boolean isFinished() {
        return moduleContext.isFinished();
    }

    public void setAppendedPrompt(AppendPromptData appendedPrompt) {
        List<AppendPromptData> appendPromptList = moduleContext.getAppendedPrompt();
        appendPromptList.addFirst(appendedPrompt);
    }

    public static HashMap<String, InteractionContext> getInstance() {
        return activeContext;
    }

    public void clearUp() {
        activeContext.remove(userId);
    }

}
