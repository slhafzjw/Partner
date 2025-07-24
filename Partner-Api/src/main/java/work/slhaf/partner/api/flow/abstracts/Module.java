package work.slhaf.partner.api.flow.abstracts;

import lombok.Getter;
import lombok.Setter;
import work.slhaf.partner.api.factory.capability.annotation.CapabilityHolder;

@CapabilityHolder
public abstract class Module {

    @Getter
    @Setter
    protected Model model = new Model();

}
