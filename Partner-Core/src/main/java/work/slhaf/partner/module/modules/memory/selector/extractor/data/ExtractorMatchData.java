package work.slhaf.partner.module.modules.memory.selector.extractor.data;

import lombok.Data;

@Data
public class ExtractorMatchData {
    private String type;
    private String text;

    public static class Constant {
        public static final String DATE = "date";
        public static final String TOPIC = "topic";
    }
}
