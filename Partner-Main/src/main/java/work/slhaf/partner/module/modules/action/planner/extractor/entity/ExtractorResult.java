package work.slhaf.partner.module.modules.action.planner.extractor.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExtractorResult {
    private boolean cacheEnabled;
    private List<String> tendencies = new ArrayList<>();
}
