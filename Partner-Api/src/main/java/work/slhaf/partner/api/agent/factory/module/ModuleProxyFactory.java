package work.slhaf.partner.api.agent.factory.module;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;
import net.bytebuddy.matcher.ElementMatchers;
import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.context.ModuleFactoryContext;
import work.slhaf.partner.api.agent.factory.module.annotation.AfterExecute;
import work.slhaf.partner.api.agent.factory.module.annotation.BeforeExecute;
import work.slhaf.partner.api.agent.factory.module.exception.ModuleInstanceGenerateFailedException;
import work.slhaf.partner.api.agent.factory.module.exception.ModuleProxyGenerateFailedException;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaMethod;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;

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

    @Override
    protected void setVariables(AgentRegisterContext context) {
        ModuleFactoryContext factoryContext = context.getModuleFactoryContext();
        moduleList = factoryContext.getModuleList();
    }

    @Override
    protected void run() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        generateInstances();
        setHookProxy();
    }

    private void setHookProxy() {
        for (MetaModule module : moduleList) {
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

    private void generateProxiedInstances(MethodsListRecord record, MetaModule metaModule) {
        try {
            Class<? extends AgentRunningModule> clazz = metaModule.getClazz();
            Class<? extends AgentRunningModule> proxyClass = new ByteBuddy()
                    .subclass(clazz)
                    .method(ElementMatchers.isOverriddenFrom(AgentRunningModule.class))
                    .intercept(MethodDelegation.to(new ModuleProxyInterceptor(record.post, record.pre)))
                    .make()
                    .load(ModuleProxyFactory.class.getClassLoader())
                    .getLoaded();
            metaModule.setInstance(proxyClass.getConstructor().newInstance());
        } catch (Exception e) {
            throw new ModuleProxyGenerateFailedException("模块Hook代理生成失败! 代理失败的模块名: " + metaModule.getClazz().getSimpleName(), e);
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
        for (MetaModule metaModule : moduleList) {
            try {
                Class<? extends AgentRunningModule> clazz = metaModule.getClazz();
                AgentRunningModule instance = clazz.getConstructor().newInstance();
                metaModule.setInstance(instance);
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
