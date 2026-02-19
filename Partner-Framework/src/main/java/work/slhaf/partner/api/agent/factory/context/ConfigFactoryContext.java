package work.slhaf.partner.api.agent.factory.context;

import lombok.Data;
import work.slhaf.partner.api.agent.factory.config.pojo.ModelConfig;
import work.slhaf.partner.api.chat.pojo.Message;

import java.util.HashMap;
import java.util.List;

@Data
public class ConfigFactoryContext {
    private HashMap<String, List<Message>> modelPromptMap = new HashMap<>();
    private HashMap<String, ModelConfig> modelConfigMap = new HashMap<>();
}
