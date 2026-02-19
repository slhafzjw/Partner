package work.slhaf.partner.module.modules.action.dispatcher.executor.entity;

import lombok.Data;
import work.slhaf.partner.module.modules.action.interventor.entity.MetaIntervention;

import java.util.List;

@Data
public class CorrectorResult {
    private List<MetaIntervention> metaInterventionList;
}
