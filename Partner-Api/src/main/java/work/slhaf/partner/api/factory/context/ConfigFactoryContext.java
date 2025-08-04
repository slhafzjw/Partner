package work.slhaf.partner.api.factory.context;

import lombok.Data;
import work.slhaf.partner.api.common.chat.pojo.Message;
import work.slhaf.partner.api.factory.config.pojo.ModelConfig;

import java.util.HashMap;
import java.util.List;

@Data
public class ConfigFactoryContext {
    private HashMap<String, List<Message>> modelPromptMap = new HashMap<>();
    private HashMap<String, ModelConfig> modelConfigMap = new HashMap<>();
}
