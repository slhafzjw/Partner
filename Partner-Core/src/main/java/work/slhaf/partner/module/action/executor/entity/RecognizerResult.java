package work.slhaf.partner.module.action.executor.entity;

import lombok.Data;

@Data
public class RecognizerResult {
    private boolean needCorrection = false;
    private String reason;
}
