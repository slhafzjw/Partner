package work.slhaf.partner.module.modules.core;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.CoreModule;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.chat.constant.ChatConstant;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.api.chat.pojo.MetaMessage;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.module.common.entity.AppendPromptData;
import work.slhaf.partner.module.common.model.ModelConstant;
import work.slhaf.partner.module.common.module.CoreRunningModule;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;
import work.slhaf.partner.runtime.session.SessionManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static work.slhaf.partner.common.util.ExtractUtil.extractJson;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
@CoreModule
public class CoreModel extends CoreRunningModule implements ActivateModel {
    
    @InjectCapability
    private CognationCapability cognationCapability;
    private SessionManager sessionManager;
    private List<Message> appendedMessages;

    @Init
    public void init(){
        List<Message> chatMessages = this.cognationCapability.getChatMessages();
        this.getModel().setChatMessages(chatMessages);
        this.appendedMessages = new ArrayList<>();
        this.sessionManager = SessionManager.getInstance();

        updateChatClientSettings();
        log.info("[CoreModel] CoreModel注册完毕...");
    }

    @Override
    public void updateChatClientSettings() {
        chatClient().setTemperature(0.3);
        chatClient().setTop_p(0.7);
    }

    @Override
    public boolean withBasicPrompt() {
        return true;
    }

    @Override
    public void execute(PartnerRunningFlowContext runningFlowContext) {
        String userId = runningFlowContext.getUserId();
        log.debug("[CoreModel] 主对话流程开始: {}", userId);
        beforeChat(runningFlowContext);
        executeChat(runningFlowContext);
        log.debug("[CoreModel] 主对话流程({})结束...", userId);
    }

    private void beforeChat(PartnerRunningFlowContext runningFlowContext) {
        setAppendedPromptMessage(runningFlowContext);
        activateModule(runningFlowContext);
        setMessageCount(runningFlowContext);

        log.debug("[CoreModel] 当前消息列表大小: {}", chatMessages().size());
        log.debug("[CoreModel] 当前核心prompt内容: {}", runningFlowContext.getCoreContext().toString());

        setMessage(runningFlowContext.getCoreContext().toString());
    }

    private void setAppendedPromptMessage(PartnerRunningFlowContext runningFlowContext) {
        List<AppendPromptData> appendedPrompt = runningFlowContext.getModuleContext().getAppendedPrompt();
        int appendedPromptSize = getAppendedPromptSize(appendedPrompt);
        if (appendedPromptSize > 0) {
            setAppendedPromptMessage(appendedPrompt);
        }
    }

    private void executeChat(PartnerRunningFlowContext runningFlowContext) {
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
                updateModuleContextAndChatMessages(runningFlowContext, response.getString("text"), chatResponse);
                break;
            } catch (Exception e) {
                count++;
                log.error("[CoreModel] CoreModel执行异常: {}", e.getLocalizedMessage());
                if (count > 3) {
                    handleExceptionResponse(response, "主模型交互出错: " + e.getLocalizedMessage());
                    chatMessages().removeLast();
                    break;
                }
            } finally {
                updateCoreResponse(runningFlowContext, response);
                resetAppendedMessages();
                log.debug("[CoreModel] 消息列表更新大小: {}", chatMessages().size());
            }
        }
    }

    private int getAppendedPromptSize(List<AppendPromptData> appendedPrompt) {
        int size = 0;
        for (AppendPromptData data : appendedPrompt) {
            size += data.getAppendedPrompt().size();
        }
        return size;
    }

    private void activateModule(PartnerRunningFlowContext context) {
        for (AppendPromptData data : context.getModuleContext().getAppendedPrompt()) {
            if (data.getAppendedPrompt().isEmpty()) continue;
            context.getCoreContext().activateModule(data.getModuleName());
        }
    }

    private void updateCoreResponse(PartnerRunningFlowContext runningFlowContext, JSONObject response) {
        runningFlowContext.getCoreResponse().put("text", response.getString("text"));
    }

    private void resetAppendedMessages() {
        this.appendedMessages.clear();
    }

    @Override
    public ChatResponse chat() {
        List<Message> temp = new ArrayList<>(baseMessages().subList(0, baseMessages().size() - 2));
        temp.addAll(appendedMessages);
        temp.addAll(baseMessages().subList(baseMessages().size() - 2, baseMessages().size()));
        temp.addAll(chatMessages());
        return chatClient().runChat(temp);
    }

    private void updateModuleContextAndChatMessages(PartnerRunningFlowContext runningFlowContext, String response, ChatResponse chatResponse) {
        cognationCapability.getMessageLock().lock();
        chatMessages().removeIf(m -> {
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
        Message primaryUserMessage = new Message(ChatConstant.Character.USER, runningFlowContext.getCoreContext().getText() + dateTime);
        chatMessages().add(primaryUserMessage);
        Message assistantMessage = new Message(ChatConstant.Character.ASSISTANT, response);
        chatMessages().add(assistantMessage);
        cognationCapability.getMessageLock().unlock();
        //设置上下文
        runningFlowContext.getModuleContext().getExtraContext().put("total_token", chatResponse.getUsageBean().getTotal_tokens());
        //区分单人聊天场景
        if (runningFlowContext.isSingle()) {
            MetaMessage metaMessage = new MetaMessage(primaryUserMessage, assistantMessage);
            sessionManager.addMetaMessage(runningFlowContext.getUserId(), metaMessage);
        }
    }

    private void setMessage(String coreContextStr) {
        Message userMessage = new Message(ChatConstant.Character.USER, coreContextStr);
        chatMessages().add(userMessage);
    }

    private void handleExceptionResponse(JSONObject response, String chatResponse) {
        response.put("text", chatResponse);
//        interactionContext.setFinished(true);
    }

    private void setMessageCount(PartnerRunningFlowContext runningFlowContext) {
        runningFlowContext.getModuleContext().getExtraContext().put("message_count", chatMessages().size());
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
