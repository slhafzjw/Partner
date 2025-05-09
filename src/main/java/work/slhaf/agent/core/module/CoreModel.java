package work.slhaf.agent.core.module;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.constant.ChatConstant;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.chat.pojo.MetaMessage;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.memory.MemoryManager;
import work.slhaf.agent.core.session.SessionManager;

import java.io.IOException;

import static work.slhaf.agent.common.util.ExtractUtil.extractJson;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class CoreModel extends Model implements InteractionModule {

    public static final String MODEL_KEY = "core_model";
    private static CoreModel coreModel;

    private MemoryManager memoryManager;
    private SessionManager sessionManager;
    private String promptCache;

    private CoreModel() {
    }

    public static CoreModel getInstance() throws IOException, ClassNotFoundException {
        if (coreModel == null) {
            Config config = Config.getConfig();
            coreModel = new CoreModel();
            coreModel.memoryManager = MemoryManager.getInstance();
            coreModel.messages = coreModel.memoryManager.getChatMessages();
            setModel(config, coreModel, MODEL_KEY, ModelConstant.CORE_MODEL_PROMPT);
            log.info("CoreModel注册完毕...");
        }
        return coreModel;
    }

    @Override
    public void execute(InteractionContext interactionContext) {
        String tempPrompt = interactionContext.getModulePrompt().toString();
        if (!tempPrompt.equals(promptCache)) {
            coreModel.getMessages().set(0, new Message(ChatConstant.Character.SYSTEM, ModelConstant.CORE_MODEL_PROMPT + "\r\n" + tempPrompt));
            promptCache = tempPrompt;
        }
        Message userMessage = new Message(ChatConstant.Character.USER, interactionContext.getCoreContext().toString());
        this.messages.add(userMessage);
        JSONObject response = null;
        int count = 0;
        while (true) {
            try {
                ChatResponse chatResponse = this.chat();
                response = JSONObject.parse(extractJson(chatResponse.getMessage()));
                this.messages.removeLast();
                this.messages.add(new Message(ChatConstant.Character.USER, interactionContext.getCoreContext().getString("text")));
                Message assistantMessage = new Message(ChatConstant.Character.ASSISTANT, response.getString("text"));
                this.messages.add(assistantMessage);

                //设置上下文
                interactionContext.getModuleContext().put("total_token", chatResponse.getUsageBean().getTotal_tokens());
                //区分单人聊天场景
                if (interactionContext.isSingle()) {
                    MetaMessage metaMessage = new MetaMessage(userMessage, assistantMessage);
                    sessionManager.addMetaMessage(interactionContext.getUserId(), metaMessage);
                }
                break;
            } catch (Exception e) {
                count++;
                log.error("CoreModel执行异常: {}", e.getLocalizedMessage());
                if (count > 3) {
                    response = new JSONObject();
                    response.put("text", "主模型交互出错: " + e.getLocalizedMessage());
                    interactionContext.setFinished(true);
                    break;
                }
            } finally {
                interactionContext.setCoreResponse(response);
            }
        }
    }
}
