package work.slhaf.partner.api.agent.factory.module.abstracts;

import lombok.Getter;
import lombok.Setter;
import work.slhaf.partner.api.agent.runtime.interaction.flow.entity.Model;

/**
 * 模块基类
 */
public abstract class Module {

    @Getter
    @Setter
    protected Model model = new Model();

}
