package work.slhaf.agent.module.modules.core;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.chat.constant.ChatConstant;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.chat.pojo.MetaMessage;
import work.slhaf.agent.core.interaction.data.context.InteractionContext;
import work.slhaf.agent.core.interaction.module.InteractionModule;
import work.slhaf.agent.core.memory.MemoryManager;
import work.slhaf.agent.core.session.SessionManager;
import work.slhaf.agent.module.common.AppendPromptData;
import work.slhaf.agent.module.common.Model;
import work.slhaf.agent.module.common.ModelConstant;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static work.slhaf.agent.common.util.ExtractUtil.extractJson;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class CoreModel extends Model implements InteractionModule {

    public static final String MODEL_KEY = "core_model";
    private static volatile CoreModel coreModel;

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
                    coreModel.appendedMessages = new ArrayList<>();
                    coreModel.sessionManager = SessionManager.getInstance();
                    setModel(coreModel, MODEL_KEY, ModelConstant.Prompt.CORE, true);
                    coreModel.updateChatClientSettings();
                    log.info("[CoreModel] CoreModel注册完毕...");
                }
            }
        }
        return coreModel;
    }

    @Override
    protected void updateChatClientSettings() {
        this.chatClient.setTemperature(0.3);
        this.chatClient.setTop_p(0.7);
    }

    @Override
    public void execute(InteractionContext interactionContext) {
        String userId = interactionContext.getUserId();
        log.debug("[CoreModel] 主对话流程开始: {}", userId);
        List<AppendPromptData> appendedPrompt = interactionContext.getModuleContext().getAppendedPrompt();
        int appendedPromptSize = getAppendedPromptSize(appendedPrompt);
        if (appendedPromptSize > 0) {
            setAppendedPromptMessage(appendedPrompt);
        }
        activateModule(interactionContext);
        setMessageCount(interactionContext);

        log.debug("[CoreModel] 当前消息列表大小: {}", this.chatMessages.size());
        log.debug("[CoreModel] 当前核心prompt内容: {}", interactionContext.getCoreContext().toString());

        setMessage(interactionContext.getCoreContext().toString());
        JSONObject response = new JSONObject();

        int count = 0;
        while (true) {
            try {
                ChatResponse chatResponse = this.chat();
                try {
                    response.putAll(JSONObject.parse(extractJson(chatResponse.getMessage())));
                } catch (Exception e) {
                    log.warn("主模型回复格式出错, 将直接作为消息返回, 建议尝试更换主模型...");
                    handleExceptionResponse(response, chatResponse.getMessage());
                }
                log.debug("[CoreModel] CoreModel 响应内容: {}", response);
                updateModuleContextAndChatMessages(interactionContext, response.getString("text"), chatResponse);
                break;
            } catch (Exception e) {
                count++;
                log.error("[CoreModel] CoreModel执行异常: {}", e.getLocalizedMessage());
                if (count > 3) {
                    handleExceptionResponse(response, "主模型交互出错: " + e.getLocalizedMessage());
                    this.chatMessages.removeLast();
                    break;
                }
            } finally {
                updateCoreResponse(interactionContext, response);
                resetAppendedMessages();
                log.debug("[CoreModel] 消息列表更新大小: {}", this.chatMessages.size());
            }
        }
        log.debug("[CoreModel] 主对话流程({})结束...", userId);
    }

    private int getAppendedPromptSize(List<AppendPromptData> appendedPrompt) {
        int size = 0;
        for (AppendPromptData data : appendedPrompt) {
            size += data.getAppendedPrompt().size();
        }
        return size;
    }

    private void activateModule(InteractionContext context) {
        HashMap<String, Boolean> activeModules = context.getCoreContext().getActiveModules();
        for (AppendPromptData data : context.getModuleContext().getAppendedPrompt()) {
            if (data.getAppendedPrompt().isEmpty()) continue;
            activeModules.put(data.getModuleName(), true);
        }
    }

    private void updateCoreResponse(InteractionContext interactionContext, JSONObject response) {
        interactionContext.getCoreResponse().put("text", response.getString("text"));
    }

    private void resetAppendedMessages() {
        this.appendedMessages.clear();
    }

    @Override
    protected ChatResponse chat() {
        List<Message> temp = new ArrayList<>(baseMessages.subList(0, baseMessages.size() - 2));
        temp.addAll(appendedMessages);
        temp.addAll(baseMessages.subList(baseMessages.size() - 2, baseMessages.size()));
        temp.addAll(chatMessages);
        return this.chatClient.runChat(temp);
    }

    private void updateModuleContextAndChatMessages(InteractionContext interactionContext, String response, ChatResponse chatResponse) {
        memoryManager.getMessageLock().lock();
        this.chatMessages.removeIf(m -> {
            if (m.getRole().equals(ChatConstant.Character.ASSISTANT)) {
                return false;
            }
            try {
                JSONObject.parseObject(extractJson(m.getContent()));
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        //添加时间标志
        String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("\r\n**[yyyy-MM-dd HH:mm:ss]"));
        Message primaryUserMessage = new Message(ChatConstant.Character.USER, interactionContext.getCoreContext().getText() + dateTime);
        this.chatMessages.add(primaryUserMessage);
        Message assistantMessage = new Message(ChatConstant.Character.ASSISTANT, response);
        this.chatMessages.add(assistantMessage);
        memoryManager.getMessageLock().unlock();
        //设置上下文
        interactionContext.getModuleContext().getExtraContext().put("total_token", chatResponse.getUsageBean().getTotal_tokens());
        //区分单人聊天场景
        if (interactionContext.isSingle()) {
            MetaMessage metaMessage = new MetaMessage(primaryUserMessage, assistantMessage);
            sessionManager.addMetaMessage(interactionContext.getUserId(), metaMessage);
        }
    }

    private void setMessage(String coreContextStr) {
        Message userMessage = new Message(ChatConstant.Character.USER, coreContextStr);
        this.chatMessages.add(userMessage);
    }

    private void handleExceptionResponse(JSONObject response, String chatResponse) {
        response.put("text", chatResponse);
//        interactionContext.setFinished(true);
    }

    private void setMessageCount(InteractionContext interactionContext) {
        interactionContext.getModuleContext().getExtraContext().put("message_count", chatMessages.size());
    }

    private void setAppendedPromptMessage(List<AppendPromptData> appendPrompt) {
        Message appendDeclareMessage = Message.builder()
                .role(ChatConstant.Character.USER)
                .content(ModelConstant.CharacterPrefix.SYSTEM + "认知补充开始")
                .build();
        this.appendedMessages.add(appendDeclareMessage);
        for (AppendPromptData data : appendPrompt) {
            setStartMessage(data);
            setContentMessage(data);
            setEndMessage(data);
            setAssistantMessage();
        }
        Message appendEndMessage = Message.builder()
                .role(ChatConstant.Character.USER)
                .content(ModelConstant.CharacterPrefix.SYSTEM + "认知补充结束")
                .build();
        this.appendedMessages.add(appendEndMessage);
    }

    private void setAssistantMessage() {
        appendedMessages.add(Message.builder()
                .role(ChatConstant.Character.ASSISTANT)
                .content("嗯，明白了")
                .build());
    }

    private void setEndMessage(AppendPromptData data) {
        Message endMessage = Message.builder()
                .role(ChatConstant.Character.USER)
                .content(ModelConstant.CharacterPrefix.SYSTEM + data.getModuleName() + "认知补充结束.")
                .build();
        appendedMessages.add(endMessage);
    }

    private void setContentMessage(AppendPromptData data) {
        data.getAppendedPrompt().forEach((k, v) -> {
            Message contentMessage = Message.builder()
                    .role(ChatConstant.Character.USER)
                    .content(ModelConstant.CharacterPrefix.SYSTEM + k + v + "\r\n")
                    .build();
            appendedMessages.add(contentMessage);
        });
    }

    private void setStartMessage(AppendPromptData data) {
        Message startMessage = Message.builder()
                .role(ChatConstant.Character.USER)
                .content(ModelConstant.CharacterPrefix.SYSTEM + data.getModuleName() + "以下为" + data.getModuleName() + "相关认知.")
                .build();
        appendedMessages.add(startMessage);
    }
}
