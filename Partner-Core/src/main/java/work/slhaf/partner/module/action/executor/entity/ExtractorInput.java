package work.slhaf.partner.module.action.executor.entity;

import lombok.Data;
import work.slhaf.partner.core.action.entity.MetaActionInfo;

@Data
public class ExtractorInput {

    /**
     * 目标行动数据的 uuid
     */
    private String targetActionId;

    /**
     * 目标行动的 description
     */
    private String targetActionDesc;

    /**
     * 目标 MetaActionInfo
     */
    private MetaActionInfo metaActionInfo;
}
