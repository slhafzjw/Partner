package work.slhaf.agent.module.modules.core;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.constant.ChatConstant;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.chat.pojo.MetaMessage;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.memory.MemoryManager;
import work.slhaf.agent.core.session.SessionManager;
import work.slhaf.agent.module.common.AppendPromptData;
import work.slhaf.agent.module.common.Model;
import work.slhaf.agent.module.common.ModelConstant;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static work.slhaf.agent.common.util.ExtractUtil.extractJson;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class CoreModel extends Model implements InteractionModule {

    public static final String MODEL_KEY = "core_model";
    private static volatile CoreModel coreModel;
    private static List<Message> baseMessagesCache;

    private MemoryManager memoryManager;
    private SessionManager sessionManager;
    private List<Message> appendedMessages;

    private CoreModel() {
    }

    public static CoreModel getInstance() throws IOException, ClassNotFoundException {
        if (coreModel == null) {
            synchronized (CoreModel.class) {
                if (coreModel == null) {
                    coreModel = new CoreModel();
                    coreModel.memoryManager = MemoryManager.getInstance();
                    coreModel.chatMessages = coreModel.memoryManager.getChatMessages();
                    coreModel.sessionManager = SessionManager.getInstance();
                    setModel(coreModel, MODEL_KEY, ModelConstant.Prompt.CORE, true);
                    baseMessagesCache = coreModel.getBaseMessages();
                    coreModel.updateChatClientSettings();
                    log.info("[CoreModel] CoreModel注册完毕...");
                }
            }
        }
        return coreModel;
    }

    @Override
    public void execute(InteractionContext interactionContext) {
        log.debug("[CoreModel] 主对话流程开始...");
        List<AppendPromptData> appendedPrompt = interactionContext.getAppendedPrompt();
        if (!appendedPrompt.isEmpty()) {
            setAppendedPromptMessage(appendedPrompt);
        }
        setMessageCount(interactionContext);

        log.debug("[CoreModel] 当前消息列表大小: {}", this.chatMessages.size());
        log.debug("[CoreModel] 当前核心prompt内容: {}", interactionContext.getCoreContext().toString());

//        Message strengthenMessage = new Message(ChatConstant.Character.SYSTEM, STRENGTHEN_PROMPT);
        setMessage(/*strengthenMessage, */interactionContext.getCoreContext().toString());
        JSONObject response = new JSONObject();

        int count = 0;
        while (true) {
            try {
                ChatResponse chatResponse = this.chat();
                try {
                    response.putAll(JSONObject.parse(extractJson(chatResponse.getMessage())));
                } catch (Exception e) {
                    log.warn("主模型回复格式出错, 将直接作为消息返回, 建议尝试更换主模型...");
                    handleExceptionResponse(response, chatResponse.getMessage(), interactionContext);
                    break;
                }
                log.debug("[CoreModel] CoreModel 响应内容: {}", response);
                handleResponse(interactionContext, response, chatResponse);
                break;
            } catch (Exception e) {
                count++;
                log.error("[CoreModel] CoreModel执行异常: {}", e.getLocalizedMessage());
                if (count > 3) {
                    handleExceptionResponse(response, "主模型交互出错: " + e.getLocalizedMessage(), interactionContext);
                    this.chatMessages.removeLast();
                    break;
                }
            } finally {
//                this.chatMessages.remove(strengthenMessage);
                interactionContext.setCoreResponse(response);
                resetBaseAndAppendedMessages();
                log.debug("[CoreModel] 消息列表更新大小: {}", this.chatMessages.size());
            }
        }
        log.debug("[CoreModel] 主对话流程结果: {}", interactionContext);
    }

    private void resetBaseAndAppendedMessages() {
        this.baseMessages.clear();
        this.baseMessages.addAll(baseMessagesCache);
        this.appendedMessages.clear();
    }

    @Override
    protected ChatResponse chat() {
        List<Message> temp = new ArrayList<>(baseMessages);
        temp.addAll(appendedMessages);
        temp.addAll(chatMessages);
        return this.chatClient.runChat(temp);
    }

    private void handleResponse(InteractionContext interactionContext, JSONObject response, ChatResponse chatResponse) {
        this.chatMessages.removeLast();
        Message primaryUserMessage = new Message(ChatConstant.Character.USER, interactionContext.getCoreContext().getString("text"));
        this.chatMessages.add(primaryUserMessage);
        Message assistantMessage = new Message(ChatConstant.Character.ASSISTANT, response.getString("text"));
        this.chatMessages.add(assistantMessage);
        //设置上下文
        interactionContext.getModuleContext().put("total_token", chatResponse.getUsageBean().getTotal_tokens());
        //区分单人聊天场景
        if (interactionContext.isSingle()) {
            MetaMessage metaMessage = new MetaMessage(primaryUserMessage, assistantMessage);
            sessionManager.addMetaMessage(interactionContext.getUserId(), metaMessage);
        }
    }

    private void setMessage(/*Message strengthenMessage,*/ String coreContextStr) {
//        this.chatMessages.add(strengthenMessage);
        Message userMessage = new Message(ChatConstant.Character.USER, coreContextStr);
        this.chatMessages.add(userMessage);
    }

    private void handleExceptionResponse(JSONObject response, String chatResponse, InteractionContext interactionContext) {
        response.put("text", chatResponse);
        interactionContext.setFinished(true);
    }

    private void setMessageCount(InteractionContext interactionContext) {
        int moduleMessageCount = appendedMessages.size();
        int messageCount = chatMessages.size() - moduleMessageCount;
        interactionContext.getModuleContext().put("message_count", messageCount);
    }

    private void setAppendedPromptMessage(List<AppendPromptData> appendPrompt) {
        Message appendDeclareMessage = Message.builder()
                .role(ChatConstant.Character.USER)
                .content(ModelConstant.CharacterPrefix.SYSTEM + "以下为追加字段声明，可能包含用户的输入字段和你需要在回应中添加的输出字段.")
                .build();
        this.appendedMessages.add(appendDeclareMessage);
        for (AppendPromptData data : appendPrompt) {
            StringBuilder str = new StringBuilder(data.getComment()).append("\r\n");
            data.getAppendedPrompt().forEach((k, v) -> str.append(k).append(": ").append(v).append("\r\n"));
            appendedMessages.add(new Message(ChatConstant.Character.USER, str.toString()));
        }
        Message appendEndMessage = Message.builder()
                .role(ChatConstant.Character.USER)
                .content(ModelConstant.CharacterPrefix.SYSTEM + "追加字段声明结束，接下来为用户的真实输入。")
                .build();
        this.appendedMessages.add(appendEndMessage);
    }
}
