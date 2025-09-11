package work.slhaf.partner.api.agent.factory.module;

import cn.hutool.core.util.ClassUtil;
import org.reflections.Reflections;
import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.context.ModuleFactoryContext;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 负责扫描<code>@Module</code>注解获取模块实例
 */
public class ModuleRegisterFactory extends AgentBaseFactory {

    private Reflections reflections;
    private List<MetaModule> moduleList;
    private List<MetaSubModule> subModuleList;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        ModuleFactoryContext factoryContext = context.getModuleFactoryContext();
        reflections = context.getReflections();
        moduleList = factoryContext.getAgentModuleList();
        subModuleList = factoryContext.getAgentSubModuleList();
    }

    @Override
    protected void run() {
        setModuleList();
        setSubModuleList();
    }

    private void setSubModuleList() {
        Set<Class<?>> subModules = reflections.getTypesAnnotatedWith(AgentSubModule.class);
        for (Class<?> subModule : subModules) {
            if (!ClassUtil.isNormalClass(subModule)) {
                continue;
            }
            Class<? extends AgentRunningSubModule> clazz = subModule.asSubclass(AgentRunningSubModule.class);
            MetaSubModule metaSubModule = new MetaSubModule();
            metaSubModule.setClazz(clazz);
            subModuleList.add(metaSubModule);
        }
    }

    private void setModuleList() {
        //反射扫描获取@AgentModule所在类, 该部分为Agent流程执行模块
        Set<Class<?>> modules = reflections.getTypesAnnotatedWith(AgentModule.class);
        for (Class<?> module : modules) {
            if (!ClassUtil.isNormalClass(module)) {
                continue;
            }
            Class<? extends AgentRunningModule> clazz = module.asSubclass(AgentRunningModule.class);
            AgentModule agentModule = clazz.getAnnotation(AgentModule.class);
            MetaModule metaModule = new MetaModule();
            metaModule.setName(agentModule.name());
            metaModule.setOrder(agentModule.order());
            metaModule.setClazz(clazz);
            moduleList.add(metaModule);
        }
        moduleList.sort(Comparator.comparing(MetaModule::getOrder));
    }

}
