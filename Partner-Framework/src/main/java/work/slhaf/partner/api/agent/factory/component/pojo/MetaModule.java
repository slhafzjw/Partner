package work.slhaf.partner.api.agent.factory.component.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class MetaModule extends BaseMetaModule<AbstractAgentRunningModule> {
    private String name;
    private int order;
    private boolean enabled = true;
}
