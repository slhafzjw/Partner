package work.slhaf.agent.modules.topic;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;

@EqualsAndHashCode(callSuper = true)
@Data
public class TopicExtractor extends Model {
    public static final String MODEL_KEY = "topic_extractor";
    private static TopicExtractor topicExtractor;

    public static TopicExtractor initialize(Config config) {

        if (topicExtractor == null) {
            topicExtractor = new TopicExtractor();
            topicExtractor.setPrompt(ModelConstant.SLICE_EVALUATOR_PROMPT);
            setModel(config,topicExtractor, MODEL_KEY, topicExtractor.getPrompt());
        }

        return topicExtractor;
    }

}
