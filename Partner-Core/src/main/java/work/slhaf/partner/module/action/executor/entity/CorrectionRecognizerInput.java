package work.slhaf.partner.module.action.executor.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CorrectionRecognizerInput {
    private String tendency;
    private String source;
    private String reason;
    private String description;
    private String actionId;
}
