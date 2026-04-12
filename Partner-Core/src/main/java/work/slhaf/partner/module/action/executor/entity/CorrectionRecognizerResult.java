package work.slhaf.partner.module.action.executor.entity;

import lombok.Data;

@Data
public class CorrectionRecognizerResult {
    private boolean needCorrection = false;
    private String reason;
}
