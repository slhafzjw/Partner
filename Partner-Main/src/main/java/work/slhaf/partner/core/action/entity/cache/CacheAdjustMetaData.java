package work.slhaf.partner.core.action.entity.cache;

import lombok.Data;

@Data
public class CacheAdjustMetaData {
    private String tendency;
    private boolean passed;
    private boolean hit;
}
