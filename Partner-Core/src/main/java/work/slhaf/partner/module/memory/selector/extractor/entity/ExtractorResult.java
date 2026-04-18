package work.slhaf.partner.module.memory.selector.extractor.entity;

import lombok.Data;

import java.util.List;

@Data
public class ExtractorResult {
    private List<ExtractorMatchData> matches;

    @Data
    public static class ExtractorMatchData {
        private String type;
        private String text;

        public static class Constant {
            public static final String DATE = "date";
            public static final String TOPIC = "topic";
        }
    }
}
