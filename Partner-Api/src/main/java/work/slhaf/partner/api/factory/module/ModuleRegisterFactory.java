package work.slhaf.partner.api.factory.module;

import org.reflections.Reflections;
import work.slhaf.partner.api.factory.entity.AgentBaseFactory;
import work.slhaf.partner.api.factory.entity.AgentRegisterContext;
import work.slhaf.partner.api.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.factory.module.pojo.MetaModule;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 负责扫描<code>@Module</code>注解获取模块实例
 */
public class ModuleRegisterFactory extends AgentBaseFactory {

    private Reflections reflections;
    private List<MetaModule> moduleList;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        reflections = context.getReflections();
        moduleList = context.getModuleList();
    }

    @Override
    protected void run() {
        //反射扫描获取@AgentModule所在类, 该部分为Agent流程执行模块
        Set<Class<?>> modules = reflections.getTypesAnnotatedWith(AgentModule.class);
        for (Class<?> module : modules) {
            AgentModule agentModule = module.getAnnotation(AgentModule.class);
            MetaModule metaModule = new MetaModule();
            metaModule.setName(agentModule.name());
            metaModule.setOrder(agentModule.order());
            metaModule.setClazz(module);
            moduleList.add(metaModule);
        }
        moduleList.sort(Comparator.comparing(MetaModule::getOrder));
    }
}
