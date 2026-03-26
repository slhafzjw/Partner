package work.slhaf.partner.module.action.executor.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CorrectionRecognizerMetaActionSnapshot {
    private String key;
    private String name;
    private boolean io;
    private String resultStatus;
    private String resultData;
}
