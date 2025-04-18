package work.slhaf.agent.modules.topic;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.chat.constant.ChatConstant;
import work.slhaf.agent.common.chat.pojo.ChatResponse;
import work.slhaf.agent.common.chat.pojo.Message;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.data.InteractionContext;

import java.io.IOException;

@EqualsAndHashCode(callSuper = true)
@Data
public class TopicExtractor extends Model implements InteractionModule {
    public static final String MODEL_KEY = "topic_extractor";
    private static TopicExtractor topicExtractor;

    private TopicExtractor() {
    }

    public static TopicExtractor getInstance() throws IOException, ClassNotFoundException {
        if (topicExtractor == null) {
            Config config = Config.getConfig();
            topicExtractor = new TopicExtractor();
            setModel(config, topicExtractor, MODEL_KEY, ModelConstant.TOPIC_EXTRACTOR_PROMPT);
        }

        return topicExtractor;
    }

    @Override
    public void execute(InteractionContext interactionContext) {
        String primaryMessageResponse = singleChat(interactionContext.getInput()).getMessage();

    }

}
