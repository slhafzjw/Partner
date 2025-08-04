package work.slhaf.partner.api.factory.module;

import org.reflections.Reflections;
import work.slhaf.partner.api.factory.AgentBaseFactory;
import work.slhaf.partner.api.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.factory.context.ModuleFactoryContext;
import work.slhaf.partner.api.factory.module.annotation.After;
import work.slhaf.partner.api.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.factory.module.annotation.Before;
import work.slhaf.partner.api.factory.module.pojo.MetaMethod;
import work.slhaf.partner.api.factory.module.pojo.MetaModule;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 负责扫描<code>@Module</code>注解获取模块实例
 */
public class ModuleRegisterFactory extends AgentBaseFactory {

    private Reflections reflections;
    private List<MetaModule> moduleList;
    private HashMap<Class<?>, Set<MetaMethod>> postHookMethods;
    private HashMap<Class<?>, Set<MetaMethod>> preHookMethods;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        ModuleFactoryContext factoryContext = context.getModuleFactoryContext();
        reflections = context.getReflections();
        moduleList = factoryContext.getModuleList();
        postHookMethods = factoryContext.getPostHookMethods();
        preHookMethods = factoryContext.getPreHookMethods();
    }

    @Override
    protected void run() {
        registerModuleList();
        registerHookSet();
    }

    private void registerHookSet() {
        setPostHookMethods();
        setPreHookMethods();
    }

    private void setPreHookMethods() {
        Set<Method> methods = reflections.getMethodsAnnotatedWith(After.class);
        for (Method method : methods) {
            MetaMethod metaMethod = new MetaMethod();
            metaMethod.setMethod(method);
            metaMethod.setOrder(method.getAnnotation(After.class).order());

            addMetaMethod(method, metaMethod, postHookMethods);
        }
    }

    private void setPostHookMethods() {
        Set<Method> methods = reflections.getMethodsAnnotatedWith(Before.class);
        for (Method method : methods) {
            MetaMethod metaMethod = new MetaMethod();
            metaMethod.setMethod(method);
            metaMethod.setOrder(method.getAnnotation(Before.class).order());

            addMetaMethod(method, metaMethod, preHookMethods);
        }
    }

    private void addMetaMethod(Method method, MetaMethod metaMethod, HashMap<Class<?>, Set<MetaMethod>> preHookMethods) {
        Class<?> clazz = method.getDeclaringClass();
        if (preHookMethods.containsKey(clazz)) {
            preHookMethods.get(clazz).add(metaMethod);
        }else {
            HashSet<MetaMethod> metaMethods = new HashSet<>();
            metaMethods.add(metaMethod);
            preHookMethods.put(clazz, metaMethods);
        }
    }

    private void registerModuleList() {
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
