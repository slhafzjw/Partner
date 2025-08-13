package work.slhaf.partner.api.agent.factory.module;

import cn.hutool.core.util.ClassUtil;
import org.reflections.Reflections;
import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.module.annotation.AfterExecute;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.BeforeExecute;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.factory.module.exception.ModuleCheckException;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigManager;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;

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
            Set<String> promptKeySet = AgentConfigManager.INSTANCE.getModelPromptMap().keySet();
            if (!promptKeySet.containsAll(modelKeySet)) {
                modelKeySet.removeAll(promptKeySet);
                throw new ModuleCheckException("存在未配置Prompt的ActivateModel实现! 缺少Prompt的ModelKey列表: " + modelKeySet);
            }
        } catch (Exception e) {
            throw new ModuleCheckException("ActivateModel 检测出错", e);
        }
    }

    private void hookLocationCheck() {
        //检查@AfterExecute注解
        postHookLocationCheck();
        //检查@BeforeExecute注解
        preHookLocationCheck();
        //检查@Init注解
        initHookLocationCheck();
        //检查@AgentModule注解是否只位于普通类上
        agentModuleLocationCheck();
    }

    private void agentModuleLocationCheck() {
        Set<Class<?>> types = reflections.getTypesAnnotatedWith(AgentModule.class);
        for (Class<?> type : types) {
            if (!ClassUtil.isNormalClass(type)) {
                throw new ModuleCheckException("AgentModule 注解仅能位于普通类上! 异常类信息: " + type.getSimpleName());
            }
        }
    }

    private void initHookLocationCheck() {
        Set<Method> methods = reflections.getMethodsAnnotatedWith(Init.class);
        Set<Class<?>> types = methods.stream()
                .map(Method::getDeclaringClass)
                .collect(Collectors.toSet());
        checkLocation(types);
    }

    private void preHookLocationCheck() {
        Set<Method> methods = reflections.getMethodsAnnotatedWith(BeforeExecute.class);
        Set<Class<?>> types = methods.stream()
                .map(Method::getDeclaringClass)
                .collect(Collectors.toSet());
        checkLocation(types);
    }


    private void postHookLocationCheck() {
        Set<Method> methods = reflections.getMethodsAnnotatedWith(AfterExecute.class);
        Set<Class<?>> types = methods.stream()
                .map(Method::getDeclaringClass)
                .collect(Collectors.toSet());
        checkLocation(types);
    }

    private void checkLocation(Set<Class<?>> types) {
        for (Class<?> type : types) {
            if (AgentRunningModule.class.isAssignableFrom(type)) {
                continue;
            }
            if (AgentRunningSubModule.class.isAssignableFrom(type)) {
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
            if (AgentRunningModule.class.isAssignableFrom(type) && ClassUtil.isNormalClass(type)) {
                continue;
            }
            throw new ModuleCheckException("存在未继承AgentInteractionModule.class的AgentModule实现: " + type.getSimpleName());
        }
    }
}
