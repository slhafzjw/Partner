package work.slhaf.partner.api.agent.factory.module.pojo;

import lombok.Data;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;

@Data
public class MetaModule {
    private String name;
    private int order;
    private Class<? extends AgentRunningModule> clazz;
    private AgentRunningModule instance;
    private boolean enabled = true;
}
