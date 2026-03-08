package work.slhaf.partner.module.modules.action.planner.confirmer.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import work.slhaf.partner.core.action.entity.PendingActionRecord;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingDecisionItem {
    private String pendingId;
    private PendingActionRecord.Decision decision;
    private String reason;
}
