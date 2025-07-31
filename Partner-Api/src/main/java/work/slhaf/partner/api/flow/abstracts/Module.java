package work.slhaf.partner.api.flow.abstracts;

import lombok.Getter;
import lombok.Setter;
import work.slhaf.partner.api.common.chat.Model;
import work.slhaf.partner.api.factory.capability.annotation.CapabilityHolder;

/**
 * 模块基类
 */
@CapabilityHolder
public abstract class Module {

    @Getter
    @Setter
    protected Model model = new Model();

}
