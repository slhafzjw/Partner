package work.slhaf.partner.module.modules.action.executor.entity;

import lombok.Data;

@Data
public class CorrectionRecognizerResult {
    private boolean needCorrection;
    private String reason;
}
