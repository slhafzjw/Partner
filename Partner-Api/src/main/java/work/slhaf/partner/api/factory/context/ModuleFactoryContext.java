package work.slhaf.partner.api.factory.context;

import lombok.Data;
import work.slhaf.partner.api.factory.module.pojo.MetaMethod;
import work.slhaf.partner.api.factory.module.pojo.MetaModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

@Data
public class ModuleFactoryContext {
    private List<MetaModule> moduleList = new ArrayList<>();
    private HashMap<Class<?>,Set<MetaMethod>> initHookMethods = new HashMap<>();
}
