package work.slhaf.agent.modules.memory.updater.summarizer.data;

import lombok.Data;

@Data
public class SummarizeResult {
    private String summary;
    private String topicPath;
    private boolean isPrivate;
}
