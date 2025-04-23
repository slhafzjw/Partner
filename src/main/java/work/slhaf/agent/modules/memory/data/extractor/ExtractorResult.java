package work.slhaf.agent.modules.memory.data.extractor;

import lombok.Data;

import java.util.List;

@Data
public class ExtractorResult {
    private boolean recall;
    private List<ExtractorMatchData> matches;
}
