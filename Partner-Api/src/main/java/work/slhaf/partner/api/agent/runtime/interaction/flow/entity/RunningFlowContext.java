package work.slhaf.partner.api.agent.runtime.interaction.flow.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.common.entity.PersistableObject;

/**
 * 流程上下文
 */
@EqualsAndHashCode(callSuper = true)
@Data
public abstract class RunningFlowContext extends PersistableObject {
    protected int ok;
    protected String errMsg;
}
