package work.slhaf.partner.module.common.module;

import lombok.Getter;
import lombok.Setter;
import work.slhaf.partner.api.capability.module.CapabilityHolder;
import work.slhaf.partner.module.common.model.Model;

public abstract class Module extends CapabilityHolder {

    @Getter
    @Setter
    protected Model model;

}
