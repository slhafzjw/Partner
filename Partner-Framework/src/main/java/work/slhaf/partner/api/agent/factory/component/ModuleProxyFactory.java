package work.slhaf.partner.api.agent.factory.component;

import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.capability.CapabilityCheckFactory;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.api.agent.factory.component.exception.ModuleInstanceGenerateFailedException;
import work.slhaf.partner.api.agent.factory.component.exception.ModuleProxyGenerateFailedException;
import work.slhaf.partner.api.agent.factory.component.pojo.BaseMetaModule;
import work.slhaf.partner.api.agent.factory.component.pojo.MetaModule;
import work.slhaf.partner.api.agent.factory.component.pojo.MetaSubModule;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.context.CapabilityFactoryContext;
import work.slhaf.partner.api.agent.factory.context.ModuleFactoryContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * <h2>Agent启动流程 3</h2>
 *
 * <p>
 * 扫描前置模块各个hook注解生成代理对象，放入对应的list中并按照类型为键放入 {@link ModuleProxyFactory#capabilityHolderInstances} 中供后续完成能力(capability)注入
 * <p/>
 *
 * <ol>
 *
 *     <li>
 *         <p>{@link ModuleProxyFactory#createProxiedInstances()}</p>
 *         根据moduleList中的类型信息，向上查找继承链获取所有hook方法收集为{@link MethodsListRecord}，然后通过ByteBuddy根据收集到的preHook与postHook生成代理对象，放入对应的 {@link MetaModule} 对象以及 instanceMap 中
 *     </li>
 *     <li>
 *         <p>{@link ModuleProxyFactory#injectSubModule()}</p>
 *         通过反射将子模块实例注入到执行模块中带有注解 {@link InjectModule} 的字段
 *     </li>
 * </ol>
 *
 * <p>下一步流程请参阅{@link CapabilityCheckFactory}</p>
 */
public class ModuleProxyFactory extends AgentBaseFactory {

    private final HashMap<Class<?>, Object> subModuleInstances = new HashMap<>();
    private final HashMap<Class<?>, Object> moduleInstances = new HashMap<>();
    private List<MetaModule> moduleList;
    private List<MetaSubModule> subModuleList;
    private HashMap<Class<?>, Object> capabilityHolderInstances;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        ModuleFactoryContext factoryContext = context.getModuleFactoryContext();
        CapabilityFactoryContext capabilityFactoryContext = context.getCapabilityFactoryContext();
        moduleList = factoryContext.getAgentModuleList();
        subModuleList = factoryContext.getAgentSubModuleList();
        capabilityHolderInstances = capabilityFactoryContext.getCapabilityHolderInstances();
    }

    @Override
    protected void run() {
        createProxiedInstances();
        // TODO 需要同样注入到 AgentStandaloneModule
        injectSubModule();
    }

    private void injectSubModule() {
        for (MetaModule module : moduleList) {
            //因为实际上ByteBuddy生成的是module.getClazz()的子类，所以应当使用getDeclaredFields()获取字段
            Arrays.stream(module.getClazz().getDeclaredFields())
                    .filter(field -> field.isAnnotationPresent(InjectModule.class))
                    .forEach(field -> {
                        try {
                            field.setAccessible(true);
                            field.set(
                                    moduleInstances.get(module.getClazz()),
                                    subModuleInstances.get(field.getType())
                            );
                        } catch (IllegalAccessException e) {
                            throw new ModuleInstanceGenerateFailedException("模块实例注入失败", e);
                        }
                    });
        }

    }

    private void createProxiedInstances() {
        generateModuleProxy(moduleList, moduleInstances);
        generateModuleProxy(subModuleList, subModuleInstances);
        updateCapabilityHolderInstances();
    }

    private void updateCapabilityHolderInstances() {
        capabilityHolderInstances.putAll(moduleInstances);
        capabilityHolderInstances.putAll(subModuleInstances);
    }

    private void updateInstanceMap(HashMap<Class<?>, Object> instanceMap, List<? extends BaseMetaModule> list) {
        for (BaseMetaModule baseMetaModule : list) {
            instanceMap.put(baseMetaModule.getClazz(), baseMetaModule.getInstance());
        }
    }


    private void generateModuleProxy(List<? extends BaseMetaModule> list, HashMap<Class<?>, Object> instanceMap) {
        for (BaseMetaModule module : list) {
            try {
                Class<? extends AbstractAgentModule> clazz = module.getClazz();
                AbstractAgentModule instance = clazz.getConstructor().newInstance();
                module.setInstance(instance);
                instanceMap.put(module.getClazz(), instance);
            } catch (Exception e) {
                throw new ModuleProxyGenerateFailedException("模块Hook代理生成失败! 代理失败的模块名: " + module.getClazz().getSimpleName(), e);
            }
        }
    }

}
