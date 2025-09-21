package work.slhaf.partner.api.agent.factory.module;

import cn.hutool.core.util.ClassUtil;
import org.reflections.Reflections;
import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.context.ModuleFactoryContext;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.factory.module.annotation.CoreModule;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * <h2>Agent启动流程 2</h2>
 *
 * <p>
 * 负责收集 {@link AgentModule} 与 {@link AgentSubModule} 注解所在类的信息，供后续工厂完成动态代理、模块与能力注入
 * <p/>
 *
 * <ol>
 *     <li>
 *         <p>{@link ModuleRegisterFactory#setModuleList()}</p>
 *         扫描 {@link AgentModule} 注解，获取执行模块信息: 类型、模块名称({@link AgentModule#name()})，执行顺序。并按照注解的 {@link AgentModule#order()} 字段进行排序
 *     </li>
 *     <li>
 *         <p>{@link ModuleRegisterFactory#setSubModuleList()}</p>
 *         扫描 {@link AgentSubModule} 注册，获取子模块类型信息
 *     </li>
 *     <li>
 *         两种模块都将存入各自的list中，供后续模块完成注册与注入
 *     </li>
 * </ol>
 *
 * <p>下一步流程请参阅{@link ModuleProxyFactory}</p>
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
            MetaModule metaModule = getMetaModule(clazz);
            moduleList.add(metaModule);
        }
        moduleList.sort(Comparator.comparing(MetaModule::getOrder));
    }

    private static MetaModule getMetaModule(Class<? extends AgentRunningModule> clazz) {
        MetaModule metaModule = new MetaModule();
        AgentModule agentModule;
        if (clazz.isAnnotationPresent(CoreModule.class)){
            agentModule = CoreModule.class.getAnnotation(AgentModule.class);
        }else{
            agentModule = clazz.getAnnotation(AgentModule.class);
        }
        metaModule.setName(agentModule.name());
        metaModule.setOrder(agentModule.order());
        metaModule.setClazz(clazz);
        return metaModule;
    }

}
