package work.slhaf.agent.module.modules.memory.updater.summarizer.data;

import lombok.Data;

import java.util.List;

@Data
public class SummarizeResult {
    private String summary;
    private String topicPath;
    private List<String> relatedTopicPath;
    private boolean isPrivate;
}
