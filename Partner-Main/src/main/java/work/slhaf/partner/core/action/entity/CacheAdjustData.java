package work.slhaf.partner.core.action.entity;

import lombok.Data;

@Data
public class CacheAdjustData {
    private String input;
    private String tendency;
    private boolean passed;
    private boolean hit;
}
