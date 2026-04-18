package work.slhaf.partner.module.memory.updater.summarizer.entity;

import lombok.Data;
import work.slhaf.partner.module.memory.pojo.ActivationProfile;

import java.util.List;

@Data
public class MemoryTopicResult {
    private String topicPath;
    private List<String> relatedTopicPaths;
    private ActivationProfile activationProfile;
}
