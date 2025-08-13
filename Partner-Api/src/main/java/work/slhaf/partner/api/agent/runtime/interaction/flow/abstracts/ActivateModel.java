package work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts;

import cn.hutool.core.bean.BeanUtil;
import work.slhaf.partner.api.agent.factory.config.pojo.ModelConfig;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigManager;
import work.slhaf.partner.api.agent.runtime.interaction.flow.entity.Model;
import work.slhaf.partner.api.chat.ChatClient;
import work.slhaf.partner.api.chat.constant.ChatConstant;
import work.slhaf.partner.api.chat.pojo.ChatResponse;
import work.slhaf.partner.api.chat.pojo.Message;

import java.util.ArrayList;
import java.util.List;

public interface ActivateModel {

    AgentConfigManager AGENT_CONFIG_MANAGER = AgentConfigManager.INSTANCE;

    @Init
    default void modelSettings() {
        Model model = new Model();
        ModelConfig modelConfig = AgentConfigManager.INSTANCE.loadModelConfig(modelKey());
        model.setBaseMessages(withBasicPrompt() ? loadSpecificPromptAndBasicPrompt(modelKey()) : loadSpecificPrompt(modelKey()));
        model.setChatClient(new ChatClient(modelConfig.getBaseUrl(), modelConfig.getApikey(), modelConfig.getModel()));
        ((Module) this).setModel(model);
    }

    default void updateModelSettings(ChatClient newChatClient) {
        BeanUtil.copyProperties(newChatClient, chatClient());
    }

    private List<Message> loadSpecificPrompt(String modelKey) {
        return AGENT_CONFIG_MANAGER.loadModelPrompt(modelKey);
    }

    private List<Message> loadSpecificPromptAndBasicPrompt(String modelKey) {
        List<Message> messages = new ArrayList<>();
        messages.addAll(AGENT_CONFIG_MANAGER.loadModelPrompt("basic"));
        messages.addAll(AGENT_CONFIG_MANAGER.loadModelPrompt(modelKey));
        return messages;
    }

    default ChatResponse chat() {
        Model model = getModel();
        List<Message> temp = new ArrayList<>();
        temp.addAll(model.getBaseMessages());
        temp.addAll(model.getChatMessages());
        return model.getChatClient().runChat(temp);
    }

    default ChatResponse singleChat(String input) {
        Model model = getModel();
        List<Message> temp = new ArrayList<>(model.getBaseMessages());
        temp.add(new Message(ChatConstant.Character.USER, input));
        return model.getChatClient().runChat(temp);
    }

    default void updateChatClientSettings() {
        Model model = getModel();
        model.getChatClient().setTemperature(0.4);
        model.getChatClient().setTop_p(0.8);
    }

    default List<Message> chatMessages() {
        return getModel().getChatMessages();
    }

    default List<Message> baseMessages() {
        return getModel().getBaseMessages();
    }

    default ChatClient chatClient() {
        return getModel().getChatClient();
    }

    /**
     * 仅适用Module子类，否则需要重写
     *
     * @return 持有的model实例
     */
    default Model getModel() {
        return ((Module) this).getModel();
    }

    default void setModel(Model model) {
        ((Module) this).setModel(model);
    }

    String modelKey();

    boolean withBasicPrompt();

}
