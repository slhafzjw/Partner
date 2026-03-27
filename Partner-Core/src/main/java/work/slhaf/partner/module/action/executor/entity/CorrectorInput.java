package work.slhaf.partner.module.action.executor.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class CorrectorInput {
    private String tendency;
    private String source;
    private String reason;
    private String description;
    private String actionId;

    private Map<Integer, List<ActionChainItem>> actionChainOverview;

    @Data
    @AllArgsConstructor
    public static class ActionChainItem {
        private String actionKey;
        private String description;
        private String status;
    }
}
