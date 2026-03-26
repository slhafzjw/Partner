package work.slhaf.partner.module.modules.action.executor.entity;

import lombok.Data;
import work.slhaf.partner.core.action.entity.intervention.MetaIntervention;

import java.util.List;

@Data
public class CorrectorResult {
    private List<MetaIntervention> metaInterventionList;
    private String correctionReason;
}
