package work.slhaf.partner.api.flow.abstracts;

import work.slhaf.partner.api.common.chat.ChatClient;
import work.slhaf.partner.api.common.chat.constant.ChatConstant;
import work.slhaf.partner.api.common.chat.pojo.ChatResponse;
import work.slhaf.partner.api.common.chat.pojo.Message;
import work.slhaf.partner.api.factory.module.annotation.Before;

import java.util.ArrayList;
import java.util.List;

public interface ActivateModel {


    @Before
    default void modelSettings() {
//        Model model = new Model();
//        ModelConfig modelConfig = ModelConfig.load(modelKey());
//        model.setBaseMessages(withAwareness() ? ResourcesUtil.Prompt.loadPromptWithSelfAwareness(modelKey(), promptModule()) : ResourcesUtil.Prompt.loadPrompt(modelKey(), promptModule()));
//        model.setChatClient(new ChatClient(modelConfig.getBaseUrl(), modelConfig.getApikey(), modelConfig.getModel()));
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

    boolean withAwareness();

    String promptModule();
}
