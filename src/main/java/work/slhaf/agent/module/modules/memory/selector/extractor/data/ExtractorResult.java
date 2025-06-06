package work.slhaf.agent.module.modules.memory.selector.extractor.data;

import lombok.Data;

import java.util.List;

@Data
public class ExtractorResult {
    private boolean recall;
    private List<ExtractorMatchData> matches;
}
