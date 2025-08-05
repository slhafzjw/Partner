package work.slhaf.partner.api.factory.module;

import net.bytebuddy.implementation.bind.annotation.*;
import work.slhaf.partner.api.factory.AgentBaseFactory;
import work.slhaf.partner.api.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.factory.context.ModuleFactoryContext;
import work.slhaf.partner.api.factory.module.exception.ModuleInstanceGenerateFailedException;
import work.slhaf.partner.api.factory.module.exception.ModuleProxyGenerateFailedException;
import work.slhaf.partner.api.factory.module.pojo.MetaMethod;
import work.slhaf.partner.api.factory.module.pojo.MetaModule;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * 通过扫描注解<code>@BeforeExecute</code>，获取到各个模块的后hook逻辑并通过动态代理添加到执行逻辑之后
 */
public class ModuleProxyFactory extends AgentBaseFactory {

    private List<MetaModule> moduleList;
    private HashMap<Class<?>, Set<MetaMethod>> postHookMethods;
    private HashMap<Class<?>, Set<MetaMethod>> preHookMethods;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        ModuleFactoryContext factoryContext = context.getModuleFactoryContext();
        moduleList = factoryContext.getModuleList();
        postHookMethods = factoryContext.getPostHookMethods();
        preHookMethods = factoryContext.getPreHookMethods();
    }

    @Override
    protected void run() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        //TODO 填充具体逻辑
        generateInstances();
        setHookProxy();
    }

    private void setHookProxy() {
        for (MetaModule module : moduleList) {
            Class<?> clazz = module.getClazz();
            try {
                MethodsListRecord record = getHookMethodsList(clazz);
                //生成实例，
                generateProxiedInstances(record);
            } catch (Exception e) {
                throw new ModuleProxyGenerateFailedException("创建代理对象失败: " + clazz.getSimpleName(), e);
            }
        }
    }

    private void generateProxiedInstances(MethodsListRecord record) {

    }

    private MethodsListRecord getHookMethodsList(Class<?> clazz) {
        List<MetaMethod> post = new ArrayList<>();
        List<MetaMethod> pre = new ArrayList<>();
        //获取该类本身的hook逻辑
        getHookMethodsList(post, pre, clazz);
        //获取它所继承、实现的抽象类或接口, 以AgentInteractionModule、ActiveModel为终点
        List<Class<?>> classes = getExtendedClasses(clazz);
        //获取这些类中的hook逻辑
        getHookMethodsList(post, pre, classes);
        return new MethodsListRecord(post, pre);
    }

    private void getHookMethodsList(List<MetaMethod> post, List<MetaMethod> pre, List<Class<?>> classes) {

    }

    private List<Class<?>> getExtendedClasses(Class<?> clazz) {

        return null;
    }

    private void getHookMethodsList(List<MetaMethod> post, List<MetaMethod> pre, Class<?> clazz) {

    }

    private void generateInstances() {
        for (MetaModule metaModule : moduleList) {
            try {
                Class<?> clazz = metaModule.getClazz();
                Object instance = clazz.getConstructor().newInstance();
                metaModule.setInstance(instance);
            } catch (Exception e) {
                throw new ModuleInstanceGenerateFailedException("模块实例构造失败:" + e.getMessage());
            }
        }
    }

    private static class ModuleProxyInterceptor {

        private final List<MetaMethod> postHookMethods;
        private final List<MetaMethod> preHookMethods;

        private ModuleProxyInterceptor(List<MetaMethod> postHookMethods, List<MetaMethod> preHookMethods) {
            this.postHookMethods = postHookMethods;
            this.preHookMethods = preHookMethods;
        }

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
    }
}
