package work.slhaf.partner.api.agent.factory.module;

import lombok.Getter;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;
import net.bytebuddy.matcher.ElementMatchers;
import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.capability.CapabilityCheckFactory;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.context.CapabilityFactoryContext;
import work.slhaf.partner.api.agent.factory.context.ModuleFactoryContext;
import work.slhaf.partner.api.agent.factory.module.annotation.AfterExecute;
import work.slhaf.partner.api.agent.factory.module.annotation.BeforeExecute;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.api.agent.factory.module.exception.ModuleInstanceGenerateFailedException;
import work.slhaf.partner.api.agent.factory.module.exception.ModuleProxyGenerateFailedException;
import work.slhaf.partner.api.agent.factory.module.exception.ProxiedModuleRunningException;
import work.slhaf.partner.api.agent.factory.module.pojo.BaseMetaModule;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaMethod;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.Module;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static work.slhaf.partner.api.agent.util.AgentUtil.collectExtendedClasses;

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

    private List<MetaModule> moduleList;
    private List<MetaSubModule> subModuleList;
    private HashMap<Class<?>, Object> capabilityHolderInstances;
    private final HashMap<Class<?>, Object> subModuleInstances = new HashMap<>();
    private final HashMap<Class<?>, Object> moduleInstances = new HashMap<>();

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
        generateModuleProxy(moduleList, AgentRunningModule.class);
        generateModuleProxy(subModuleList, AgentRunningSubModule.class);
        updateInstanceMap(moduleInstances, moduleList);
        updateInstanceMap(subModuleInstances, subModuleList);
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


    private void generateModuleProxy(List<? extends BaseMetaModule> list, Class<? extends Module> overrideSource) {
        for (BaseMetaModule module : list) {
            Class<?> clazz = module.getClazz();
            try {
                MethodsListRecord record = collectHookMethods(clazz);
                //生成实例
                generateProxiedInstances(record, module, overrideSource);
            } catch (Exception e) {
                throw new ModuleProxyGenerateFailedException("创建代理对象失败: " + clazz.getSimpleName(), e);
            }
        }
    }

    private void generateProxiedInstances(MethodsListRecord record, BaseMetaModule module, Class<? extends Module> overrideSource) {
        try {
            Class<? extends Module> clazz = module.getClazz();
            Class<? extends Module> proxyClass = new ByteBuddy()
                    .subclass(clazz)
                    .method(ElementMatchers.isOverriddenFrom(overrideSource))
                    .intercept(MethodDelegation.to(new ModuleProxyInterceptor(record.post, record.pre)))
                    .make()
                    .load(ModuleProxyFactory.class.getClassLoader())
                    .getLoaded();

//            new ByteBuddy()
//                    .subclass(clazz)
//                    .method(ElementMatchers.isOverriddenFrom(overrideSource))
//                    .intercept(MethodDelegation.to(new ModuleProxyInterceptor(record.post, record.pre)))
//
//                    .make()
//                    .saveIn(new File("./generated-classes"));

            module.setInstance(proxyClass.getConstructor().newInstance());
        } catch (Exception e) {
            throw new ModuleProxyGenerateFailedException("模块Hook代理生成失败! 代理失败的模块名: " + module.getClazz().getSimpleName(), e);
        }
    }

    private MethodsListRecord collectHookMethods(Class<?> clazz) {
        List<MetaMethod> post = new ArrayList<>();
        List<MetaMethod> pre = new ArrayList<>();
        //获取该类本身的hook逻辑
        collectHookMethods(post, pre, clazz);
        //获取它所继承、实现的抽象类或接口, 以Module为终点，收集继承链上所有父类和接口
        Set<Class<?>> classes = collectExtendedClasses(clazz, Module.class);
        //获取这些类中的hook逻辑
        collectHookMethods(post, pre, classes);
        return new MethodsListRecord(post, pre);
    }

    private void collectHookMethods(List<MetaMethod> post, List<MetaMethod> pre, Set<Class<?>> classes) {
        for (Class<?> type : classes) {
            collectPreHookMethods(pre, type);
            collectPostHookMethods(post, type);
        }
    }

    private void collectPostHookMethods(List<MetaMethod> post, Class<?> type) {
        Set<MetaMethod> collectedPostHookMethod = Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(AfterExecute.class))
                .map(method -> {
                    MetaMethod metaMethod = new MetaMethod();
                    metaMethod.setMethod(method);
                    metaMethod.setOrder(method.getAnnotation(AfterExecute.class).order());
                    return metaMethod;
                })
                .collect(Collectors.toSet());
        post.addAll(collectedPostHookMethod);
    }

    private void collectPreHookMethods(List<MetaMethod> pre, Class<?> type) {
        Set<MetaMethod> collectedPreHookMethods = Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(BeforeExecute.class))
                .map(method -> {
                    MetaMethod metaMethod = new MetaMethod();
                    metaMethod.setMethod(method);
                    metaMethod.setOrder(method.getAnnotation(BeforeExecute.class).order());
                    return metaMethod;
                })
                .collect(Collectors.toSet());
        pre.addAll(collectedPreHookMethods);
    }


    private void collectHookMethods(List<MetaMethod> post, List<MetaMethod> pre, Class<?> clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(BeforeExecute.class)) {
                MetaMethod metaMethod = new MetaMethod();
                metaMethod.setOrder(method.getAnnotation(BeforeExecute.class).order());
                pre.add(metaMethod);
                metaMethod.setMethod(method);
            } else if (method.isAnnotationPresent(AfterExecute.class)) {
                MetaMethod metaMethod = new MetaMethod();
                metaMethod.setOrder(method.getAnnotation(AfterExecute.class).order());
                post.add(metaMethod);
                metaMethod.setMethod(method);
            }
        }
    }

    @Getter
    @SuppressWarnings("ClassCanBeRecord")
    public static class ModuleProxyInterceptor {

        private final List<MetaMethod> postHookMethods;
        private final List<MetaMethod> preHookMethods;

        public ModuleProxyInterceptor(List<MetaMethod> postHookMethods, List<MetaMethod> preHookMethods) {
            this.postHookMethods = postHookMethods;
            this.preHookMethods = preHookMethods;
        }

        @RuntimeType
        public Object intercept(@Origin Method method, @AllArguments Object[] allArguments, @SuperCall Callable<?> zuper, @This Object proxy) throws Exception {
            executeHookMethods(preHookMethods, proxy);
            Object res = zuper.call();
            executeHookMethods(postHookMethods, proxy);
            return res;
        }

        private void executeHookMethods(List<MetaMethod> hookMethods, Object proxy) {
            for (MetaMethod metaMethod : hookMethods) {
                Method m = metaMethod.getMethod();
                try {
                    m.setAccessible(true);
                    m.invoke(proxy);
                } catch (Exception e) {
                    throw new ProxiedModuleRunningException("hook方法执行异常: " + m.getDeclaringClass() + "#" + m.getName(), e);
                }
            }
        }

    }

    record MethodsListRecord(List<MetaMethod> post, List<MetaMethod> pre) {
        public MethodsListRecord {
            post.sort(Comparator.comparingInt(MetaMethod::getOrder));
            pre.sort(Comparator.comparingInt(MetaMethod::getOrder));
        }
    }
}
