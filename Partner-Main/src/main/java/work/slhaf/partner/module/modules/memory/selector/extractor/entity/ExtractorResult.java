package work.slhaf.partner.module.modules.memory.selector.extractor.entity;

import lombok.Data;

import java.util.List;

@Data
public class ExtractorResult {
    private boolean recall;
    private List<ExtractorMatchData> matches;
}
