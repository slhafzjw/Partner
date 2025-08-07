package work.slhaf.partner.api.agent.factory.context;

import lombok.Data;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;

import java.util.ArrayList;
import java.util.List;

@Data
public class ModuleFactoryContext {
    private List<MetaModule> moduleList = new ArrayList<>();
}
