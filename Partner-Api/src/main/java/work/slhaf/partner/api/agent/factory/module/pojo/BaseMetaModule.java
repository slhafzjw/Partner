package work.slhaf.partner.api.agent.factory.module.pojo;

import lombok.Data;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.Module;

@Data
public abstract class BaseMetaModule <C extends Module> {
    private Class<? extends C> clazz;
    private C instance;
}
