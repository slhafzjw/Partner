package work.slhaf.partner.module.modules.action.identifier.evaluator.entity;

import java.util.LinkedHashMap;
import java.util.List;

import lombok.Data;
import work.slhaf.partner.module.modules.action.identifier.handler.entity.InterventionType;

/**
 * 干预倾向评估结果，包含评估通过的倾向文本、对行动链的行为、指定操作的行动单元key、未通过的原因
 */
@Data
public class EvaluatorResult {
    /**
     * 是否存在通过的干预倾向
     */
    private boolean ok;
    private List<EvaluatedInterventionData> dataList;

    @Data
    public static class EvaluatedInterventionData {
        /**
         * 是否通过
         */
        private boolean ok;
        private String tendency;
        /**
         * 描述信息(包括通过、失败原因)
         */
        private String description;
        private InterventionType type;
        /**
         * 键为执行顺序，值为行动单元对应的key
         */
        private LinkedHashMap<Integer, String> actions;
    }
}
