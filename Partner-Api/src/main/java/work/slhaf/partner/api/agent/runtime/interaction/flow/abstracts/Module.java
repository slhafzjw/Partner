package work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts;

import lombok.Getter;
import lombok.Setter;
import work.slhaf.partner.api.agent.factory.capability.annotation.CapabilityHolder;
import work.slhaf.partner.api.agent.runtime.interaction.flow.entity.Model;

/**
 * 模块基类
 */
@CapabilityHolder
public abstract class Module {

    @Getter
    @Setter
    protected Model model = new Model();

}
