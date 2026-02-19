package work.slhaf.partner.core.action.entity.cache;

import lombok.Data;

import java.util.List;

@Data
public class CacheAdjustData {
    private String input;
    private List<CacheAdjustMetaData> metaDataList;
}
