package work.slhaf.partner.module.modules.action.interventor.handler.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.core.action.entity.PhaserRecord;
import work.slhaf.partner.module.modules.action.interventor.entity.MetaIntervention;

import java.util.List;

@Data
public class HandlerInput {

    private List<ExecutingInterventionData> executing;
    private List<PreparedInterventionData> prepared;

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ExecutingInterventionData extends InterventionData {
        private PhaserRecord record;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PreparedInterventionData extends InterventionData {
        private ActionData actionData;
    }

    /**
     * 针对一个干预倾向而言，有可能针对一个行动数据做出多种、不同类型的干预操作，即封装为 InterventionData 内部的 MetaIntervention 列表
     */
    @Data
    public static abstract class InterventionData {
        protected String tendency;
        protected String description;
        protected List<MetaIntervention> interventions;
    }

}
