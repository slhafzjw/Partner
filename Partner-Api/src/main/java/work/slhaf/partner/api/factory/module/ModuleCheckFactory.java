package work.slhaf.partner.api.factory.module;

import org.reflections.Reflections;
import work.slhaf.partner.api.factory.AgentBaseFactory;
import work.slhaf.partner.api.factory.config.ModelConfigManager;
import work.slhaf.partner.api.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.factory.module.annotation.After;
import work.slhaf.partner.api.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.factory.module.annotation.Before;
import work.slhaf.partner.api.factory.module.exception.ModuleCheckException;
import work.slhaf.partner.api.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.flow.abstracts.AgentInteractionModule;
import work.slhaf.partner.api.flow.abstracts.AgentInteractionSubModule;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ModuleCheckFactory extends AgentBaseFactory {

    private Reflections reflections;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        reflections = context.getReflections();
    }

    @Override
    protected void run() {
        Set<Class<?>> types = reflections.getTypesAnnotatedWith(AgentModule.class);
        //检查注解AgentModule所在类是否继承了AgentInteractionModule
        agentModuleAnnotationCheck(types);
        //检查AgentModule是否具备无参构造方法
        moduleConstructorsCheck(types);
        //检查hook注解所在方法是否位于AgentInteractionModule子类/AgentInteractionSubModule子类/ActivateModel子类
        hookLocationCheck();
        //检查实现了ActivateModel的模块数量、名称与prompt是否一致
        activateModelImplCheck();
    }

    private void moduleConstructorsCheck(Set<Class<?>> types) {
        for (Class<?> type : types) {
            try {
                type.getConstructor();
            } catch (NoSuchMethodException e) {
                throw new ModuleCheckException("缺少无参构造方法的模块: " + type.getSimpleName(), e);
            }
        }
    }

    private void activateModelImplCheck() {
        try {
            Set<Class<? extends ActivateModel>> types = reflections.getSubTypesOf(ActivateModel.class);
            Set<String> modelKeySet = new HashSet<>();
            for (Class<? extends ActivateModel> type : types) {
                ActivateModel instance = type.getConstructor().newInstance();
                modelKeySet.add(instance.modelKey());
            }
            Set<String> promptKeySet = ModelConfigManager.INSTANCE.getModelPromptMap().keySet();
            if (!promptKeySet.containsAll(modelKeySet)) {
                modelKeySet.removeAll(promptKeySet);
                throw new ModuleCheckException("存在未配置Prompt的ActivateModel实现! 缺少Prompt的ModelKey列表: " + modelKeySet);
            }
        } catch (Exception e) {
            throw new ModuleCheckException("ActivateModel 检测出错", e);
        }
    }

    private void hookLocationCheck() {
        //检查@After注解
        postHookLocationCheck();
        //检查@Before注解
        preHookLocationCheck();
    }

    private void preHookLocationCheck() {
        Set<Method> methods = reflections.getMethodsAnnotatedWith(Before.class);
        Set<Class<?>> types = methods.stream()
                .map(Method::getDeclaringClass)
                .collect(Collectors.toSet());
        checkLocation(types);
    }


    private void postHookLocationCheck() {
        Set<Method> methods = reflections.getMethodsAnnotatedWith(After.class);
        Set<Class<?>> types = methods.stream()
                .map(Method::getDeclaringClass)
                .collect(Collectors.toSet());
        checkLocation(types);
    }

    private void checkLocation(Set<Class<?>> types) {
        for (Class<?> type : types) {
            if (AgentInteractionModule.class.isAssignableFrom(type)) {
                continue;
            }
            if (AgentInteractionSubModule.class.isAssignableFrom(type)) {
                continue;
            }
            if (ActivateModel.class.isAssignableFrom(type)) {
                continue;
            }
            throw new ModuleCheckException("在不支持的类中使用了hook注解: " + type.getSimpleName());
        }
    }

    private void agentModuleAnnotationCheck(Set<Class<?>> types) {
        for (Class<?> type : types) {
            if (type.isAnnotation()) {
                continue;
            }
            if (type.isAssignableFrom(AgentInteractionModule.class)) {
                continue;
            }
            throw new ModuleCheckException("存在未继承AgentInteractionModule.class的AgentModule实现: " + type.getSimpleName());
        }
    }
}
