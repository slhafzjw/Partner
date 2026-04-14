package work.slhaf.partner.module.memory.updater.summarizer.entity;

import lombok.Data;

import java.util.List;

@Data
public class MemoryTopicResult {
    private String topicPath;
    private List<String> relatedTopicPaths;
}
