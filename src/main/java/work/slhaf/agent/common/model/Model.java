package work.slhaf.agent.common.model;

import lombok.Data;
import work.slhaf.agent.common.chat.ChatClient;
import work.slhaf.agent.common.chat.constant.Constant;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.config.ModelConfig;
import work.slhaf.agent.modules.memory.MemoryGraph;

import java.util.ArrayList;
import java.util.List;

@Data
public class Model {
    protected ChatClient chatClient;
    protected String prompt;
    protected List<Message> messages;

    protected static void setModel(Config config, Model model, String model_key, String prompt) {
        MemoryGraph memoryGraph = MemoryGraph.initialize(config.getAgentId());
        ModelConfig modelConfig = config.getModelConfig().get(model_key);
        if (memoryGraph.getModelPrompt().containsKey(model_key)) {
            model.setPrompt(memoryGraph.getModelPrompt().get(model_key));
        } else {
            model.setPrompt(prompt);
            memoryGraph.getModelPrompt().put(model_key, prompt);
        }
        if (memoryGraph.getChatMessages() == null) {
            List<Message> tempMessages = new ArrayList<>();
            tempMessages.add(new Message(Constant.Character.SYSTEM, model.getPrompt()));
            model.setMessages(tempMessages);
            memoryGraph.setChatMessages(tempMessages);
        } else {
            model.setMessages(memoryGraph.getChatMessages());
        }
        model.setChatClient(new ChatClient(modelConfig.getBaseUrl(), modelConfig.getApikey(), modelConfig.getModel()));
    }
}
