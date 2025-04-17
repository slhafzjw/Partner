package work.slhaf.agent.modules.topic;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;
import work.slhaf.module.InteractionContext;
import work.slhaf.module.InteractionModule;

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
            topicExtractor.setPrompt(ModelConstant.SLICE_EVALUATOR_PROMPT);
            setModel(config, topicExtractor, MODEL_KEY, topicExtractor.getPrompt());
        }

        return topicExtractor;
    }

    @Override
    public void execute(InteractionContext interactionContext) {

    }
}
