package work.slhaf.partner.module.action.executor.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
public class ExtractorResult {
    private boolean ok;
    private List<ParamEntry> params;

    @Data
    @AllArgsConstructor
    public static class ParamEntry {
        private String name;
        private String value;
    }
}
