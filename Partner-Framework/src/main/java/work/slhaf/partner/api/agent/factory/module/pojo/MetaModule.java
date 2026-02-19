package work.slhaf.partner.api.agent.factory.module.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.factory.module.abstracts.AbstractAgentRunningModule;

@EqualsAndHashCode(callSuper = true)
@Data
public class MetaModule extends BaseMetaModule<AbstractAgentRunningModule> {
    private String name;
    private int order;
    private boolean enabled = true;
}
