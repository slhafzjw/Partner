package work.slhaf.agent.common.model;

import lombok.Data;
import work.slhaf.agent.common.chat.ChatClient;
import work.slhaf.agent.common.chat.constant.ChatConstant;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.config.ModelConfig;
import work.slhaf.agent.common.util.ResourcesUtil;

import java.util.List;

@Data
public class Model {
    protected ChatClient chatClient;
    protected String prompt;
    protected List<Message> messages;

    protected static void setModel(Model model, String model_key, String promptModule, boolean withAwareness) {
        ModelConfig modelConfig = ModelConfig.load(model_key);

        /*if (memoryGraph.getModelPrompt().containsKey(model_key)) {
            model.setPrompt(memoryGraph.getModelPrompt().get(model_key));
        } else {
            model.setPrompt(prompt);
            memoryGraph.getModelPrompt().put(model_key, prompt);
        }
        if (memoryGraph.getChatMessages() == null) {
            List<Message> tempMessages = new ArrayList<>();
            tempMessages.add(new Message(ChatConstant.Character.SYSTEM, model.getPrompt()));
            model.setMessages(tempMessages);
            memoryGraph.setChatMessages(tempMessages);
        } else {
            model.setMessages(memoryGraph.getChatMessages());
        }*/
        model.setMessages(withAwareness ? ResourcesUtil.Prompt.loadPromptWithSelfAwareness(model_key, promptModule) : ResourcesUtil.Prompt.loadPrompt(model_key, promptModule));
        model.setChatClient(new ChatClient(modelConfig.getBaseUrl(), modelConfig.getApikey(), modelConfig.getModel()));
    }

    public ChatResponse chat() {
        return this.chatClient.runChat(this.messages);
    }

    public ChatResponse singleChat(String input) {
        return this.chatClient.runChat(List.of(
                new Message(ChatConstant.Character.SYSTEM, this.prompt),
                new Message(ChatConstant.Character.USER, input)
        ));
    }
}
