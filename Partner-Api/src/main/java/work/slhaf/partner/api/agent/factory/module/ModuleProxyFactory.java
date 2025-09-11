package work.slhaf.partner.api.agent.factory.module;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;
import net.bytebuddy.matcher.ElementMatchers;
import org.reflections.Reflections;
import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.context.ModuleFactoryContext;
import work.slhaf.partner.api.agent.factory.module.annotation.AfterExecute;
import work.slhaf.partner.api.agent.factory.module.annotation.BeforeExecute;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.api.agent.factory.module.exception.ModuleInstanceGenerateFailedException;
import work.slhaf.partner.api.agent.factory.module.exception.ModuleProxyGenerateFailedException;
import work.slhaf.partner.api.agent.factory.module.pojo.BaseMetaModule;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaMethod;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static work.slhaf.partner.api.agent.util.AgentUtil.collectExtendedClasses;

/**
 * 通过扫描注解<code>@BeforeExecute</code>，获取到各个模块的后hook逻辑并通过动态代理添加到执行逻辑之后
 */
public class ModuleProxyFactory extends AgentBaseFactory {

    private List<MetaModule> moduleList;
    private List<MetaSubModule> subModuleList;
    private Reflections reflections;
    private final HashMap<Class<?>, Object> subModuleInstances = new HashMap<>();
    private final HashMap<Class<?>, Object> moduleInstances = new HashMap<>();

    @Override
    protected void setVariables(AgentRegisterContext context) {
        ModuleFactoryContext factoryContext = context.getModuleFactoryContext();
        moduleList = factoryContext.getAgentModuleList();
        subModuleList = factoryContext.getAgentSubModuleList();
        reflections = context.getReflections();
    }

    @Override
    protected void run() {
        generateInstances();
        createProxy();
        injectSubModule();
    }

    private void injectSubModule() {
        Set<Field> fields = reflections.getFieldsAnnotatedWith(InjectModule.class);
        try {
            for (Field field : fields) {
                field.setAccessible(true);
                field.set(moduleInstances.get(field.getDeclaringClass()), subModuleInstances.get(field.getType()));
            }
        } catch (Exception e) {
            throw new ModuleInstanceGenerateFailedException("模块实例注入失败", e);
        }
    }

    private void createProxy() {
        generateModuleProxy(moduleList);
        generateModuleProxy(subModuleList);
    }


    private void generateModuleProxy(List<? extends BaseMetaModule> list) {
        for (BaseMetaModule module : list) {
            Class<?> clazz = module.getClazz();
            try {
                MethodsListRecord record = collectHookMethods(clazz);
                //生成实例
                generateProxiedInstances(record, module);
            } catch (Exception e) {
                throw new ModuleProxyGenerateFailedException("创建代理对象失败: " + clazz.getSimpleName(), e);
            }
        }
    }

    private void generateProxiedInstances(MethodsListRecord record, BaseMetaModule module) {
        try {
            Class<? extends AgentRunningModule> clazz = module.getClazz();
            Class<? extends AgentRunningModule> proxyClass = new ByteBuddy()
                    .subclass(clazz)
                    .method(ElementMatchers.isOverriddenFrom(AgentRunningModule.class))
                    .intercept(MethodDelegation.to(new ModuleProxyInterceptor(record.post, record.pre)))
                    .make()
                    .load(ModuleProxyFactory.class.getClassLoader())
                    .getLoaded();
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
        //获取它所继承、实现的抽象类或接口, 以AgentInteractionModule、ActiveModel为终点
        Set<Class<?>> classes = collectExtendedClasses(clazz, AgentRunningModule.class);
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
        Method[] methods = clazz.getMethods();
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

    private void generateInstances() {
        for (MetaModule module : moduleList) {
            try {
                Class<? extends AgentRunningModule> clazz = module.getClazz();
                AgentRunningModule instance = clazz.getConstructor().newInstance();
                module.setInstance(instance);
                moduleInstances.put(module.getClazz(), instance);
            } catch (Exception e) {
                throw new ModuleInstanceGenerateFailedException("模块实例构造失败:" + e.getMessage());
            }
        }

        for (MetaSubModule module : subModuleList) {
            try {
                Class<? extends AgentRunningSubModule> clazz = module.getClazz();
                AgentRunningSubModule instance = clazz.getConstructor().newInstance();
                module.setInstance(instance);
                subModuleInstances.put(module.getClazz(), instance);
            } catch (Exception e) {
                throw new ModuleInstanceGenerateFailedException("模块实例构造失败:" + e.getMessage());
            }
        }
    }

    private record ModuleProxyInterceptor(List<MetaMethod> postHookMethods, List<MetaMethod> preHookMethods) {
        @RuntimeType
        public Object intercept(@Origin Method method, @AllArguments Object[] allArguments, @SuperCall Callable<?> zuper, @This Object proxy) throws Exception {
            for (MetaMethod metaMethod : preHookMethods) {
                metaMethod.getMethod().invoke(proxy);
            }
            Object res = zuper.call();
            for (MetaMethod metaMethod : postHookMethods) {
                metaMethod.getMethod().invoke(proxy);
            }
            return res;
        }
    }

    record MethodsListRecord(List<MetaMethod> post, List<MetaMethod> pre) {
        public MethodsListRecord {
            post.sort(Comparator.comparingInt(MetaMethod::getOrder));
            pre.sort(Comparator.comparingInt(MetaMethod::getOrder));
        }
    }
}
