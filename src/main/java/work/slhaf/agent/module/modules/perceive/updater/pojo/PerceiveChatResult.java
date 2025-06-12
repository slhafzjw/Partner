package work.slhaf.agent.module.modules.perceive.updater.pojo;

import lombok.Data;

import java.util.List;

@Data
public class PerceiveChatResult {
    private String relation;
    private List<String> impressions;
    private List<String> attitude;
    private List<String> staticMemory;
}
