package work.slhaf.partner.api.agent.factory.module;

import org.reflections.Reflections;
import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.context.ModuleFactoryContext;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;

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
        ModuleFactoryContext factoryContext = context.getModuleFactoryContext();
        reflections = context.getReflections();
        moduleList = factoryContext.getModuleList();
    }

    @Override
    protected void run() {
        setModuleList();
    }

    private void setModuleList() {
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
