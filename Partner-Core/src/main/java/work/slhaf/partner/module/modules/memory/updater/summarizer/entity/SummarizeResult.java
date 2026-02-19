package work.slhaf.partner.module.modules.memory.updater.summarizer.entity;

import lombok.Data;

import java.util.List;

@Data
public class SummarizeResult {
    private String summary;
    private String topicPath;
    private List<String> relatedTopicPath;
    private boolean isPrivate;
}
