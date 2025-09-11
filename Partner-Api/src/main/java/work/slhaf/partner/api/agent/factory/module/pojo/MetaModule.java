package work.slhaf.partner.api.agent.factory.module.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;

@EqualsAndHashCode(callSuper = true)
@Data
public class MetaModule extends BaseMetaModule<AgentRunningModule>{
    private String name;
    private int order;
    private boolean enabled = true;
}
