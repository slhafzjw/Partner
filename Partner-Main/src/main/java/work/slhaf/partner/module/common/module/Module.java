package work.slhaf.partner.module.common.module;

import lombok.Getter;
import lombok.Setter;
import work.slhaf.partner.api.capability.annotation.CapabilityHolder;
import work.slhaf.partner.module.common.model.Model;

@CapabilityHolder
public abstract class Module  {

    @Getter
    @Setter
    protected Model model;

}
