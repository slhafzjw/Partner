package work.slhaf.partner.api.agent.factory.component.pojo;

import lombok.Data;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;

@Data
public abstract class BaseMetaModule<C extends AbstractAgentModule> {
    private Class<? extends C> clazz;
    private C instance;
}
