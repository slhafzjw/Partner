package work.slhaf.partner.api.factory.module;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;
import net.bytebuddy.matcher.ElementMatchers;
import work.slhaf.partner.api.factory.AgentBaseFactory;
import work.slhaf.partner.api.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.factory.context.ModuleFactoryContext;
import work.slhaf.partner.api.factory.module.exception.ModuleInstanceGenerateFailedException;
import work.slhaf.partner.api.factory.module.exception.ModuleProxyGenerateFailedException;
import work.slhaf.partner.api.factory.module.pojo.MetaMethod;
import work.slhaf.partner.api.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.flow.abstracts.AgentInteractionModule;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * 通过扫描注解<code>@Before</code>，获取到各个模块的后hook逻辑并通过动态代理添加到执行逻辑之后
 */
public class ModuleProxyFactory extends AgentBaseFactory {

    private List<MetaModule> moduleList;
    private HashMap<Class<?>, Set<MetaMethod>> postHookMethods;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        ModuleFactoryContext factoryContext = context.getModuleFactoryContext();
        moduleList = factoryContext.getModuleList();
        postHookMethods = factoryContext.getPostHookMethods();
    }

    @Override
    protected void run() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        //TODO 生成实例、并通过动态代理添加PostHook逻辑
        generateInstances();
        setPostHookProxy();
    }

    private void setPostHookProxy() {
        for (MetaModule module : moduleList) {
            Class<?> clazz = module.getClazz();
            try {
                Class<?> proxyClass = new ByteBuddy()
                        .subclass(clazz)
                        .method(ElementMatchers.isOverriddenFrom(AgentInteractionModule.class))
                        .intercept(MethodDelegation.to(new ModuleProxyInterceptor(postHookMethods.get(clazz).stream().sorted(Comparator.comparing(MetaMethod::getOrder)).toList())))
                        .make()
                        .load(ModuleProxyFactory.class.getClassLoader())
                        .getLoaded();

                AgentInteractionModule interactionModule = (AgentInteractionModule) proxyClass.getConstructor().newInstance();
                //TODO 检测代理写法是否正确
                //TODO 添加ModuleManager,负责统一管理Module的加载、卸载
            } catch (Exception e) {
                throw new ModuleProxyGenerateFailedException("创建代理对象失败: " + clazz.getSimpleName(), e);
            }
        }
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

        private List<MetaMethod> postHookMethods;

        private ModuleProxyInterceptor(List<MetaMethod> postHookMethods) {
            this.postHookMethods = postHookMethods;
        }

        @RuntimeType
        public Object intercept(@Origin Method method, @AllArguments Object[] allArguments, @SuperCall Callable<?> zuper, @This Object proxy) throws Exception {
            Object res = zuper.call();
            for (MetaMethod metaMethod : postHookMethods) {
                metaMethod.getMethod().invoke(proxy, allArguments);
            }
            return res;
        }
    }
}
