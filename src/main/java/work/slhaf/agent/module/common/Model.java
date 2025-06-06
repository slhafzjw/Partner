package work.slhaf.agent.module.common;

import lombok.Data;
import work.slhaf.agent.common.chat.ChatClient;
import work.slhaf.agent.common.chat.constant.ChatConstant;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.config.ModelConfig;
import work.slhaf.agent.common.util.ResourcesUtil;

import java.util.ArrayList;
import java.util.List;

@Data
public class Model {
    protected ChatClient chatClient;
    protected List<Message> chatMessages;
    protected List<Message> baseMessages;

    protected static void setModel(Model model, String model_key, String promptModule, boolean withAwareness) {
        ModelConfig modelConfig = ModelConfig.load(model_key);

        model.setBaseMessages(withAwareness ? ResourcesUtil.Prompt.loadPromptWithSelfAwareness(model_key, promptModule) : ResourcesUtil.Prompt.loadPrompt(model_key, promptModule));
        model.setChatClient(new ChatClient(modelConfig.getBaseUrl(), modelConfig.getApikey(), modelConfig.getModel()));
    }

    protected ChatResponse chat() {
        List<Message> temp = new ArrayList<>();
        temp.addAll(this.baseMessages);
        temp.addAll(this.chatMessages);
        return this.chatClient.runChat(temp);
    }

    protected ChatResponse singleChat(String input) {
        List<Message> temp = new ArrayList<>(baseMessages);
        temp.add( new Message(ChatConstant.Character.USER, input));
        return this.chatClient.runChat(temp);
    }

    protected void updateChatClientSettings() {
        this.chatClient.setTemperature(0.4);
        this.chatClient.setTop_p(0.8);
    }
}
