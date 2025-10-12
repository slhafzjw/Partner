package work.slhaf.partner.module.modules.perceive.updater.relation_extractor.entity;

import lombok.Data;

import java.util.List;

@Data
public class RelationExtractResult {
    private String relation;
    private List<String> impressions;
    private List<String> attitude;
    private String relationChangeHistory;
}
