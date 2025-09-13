package work.slhaf.partner.api.agent.factory.module;

import cn.hutool.core.util.ClassUtil;
import org.reflections.Reflections;
import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.module.annotation.*;
import work.slhaf.partner.api.agent.factory.module.exception.ModuleCheckException;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigManager;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static work.slhaf.partner.api.agent.util.AgentUtil.getMethodAnnotationTypeSet;

/**
 * <h2>Agent启动流程 1</h2>
 *
 * <p>
 * 检查模块部分抽象类与注解、接口的使用方式
 * </p>
 *
 * <ol>
 *     <li>
 *         <p>{@link ModuleCheckFactory#annotationAbstractCheck(Set, Class)}</p>
 *         所有添加了 {@link AgentModule} 注解的类都将作为Agent的执行模块，为规范模块入口，都必须实现抽象类: {@link AgentRunningModule}; {@link AgentSubModule} 注解所在类则必须实现 {@link AgentRunningSubModule}
 *     </li>
 *     <li>
 *         <p>{@link ModuleCheckFactory#moduleConstructorsCheck(Set)}</p>
 *         所有 {@link AgentModule} 与 {@link AgentSubModule} 注解所在类都必须具备空参构造方法，初始化逻辑可放在 @Init 注解所处方法中，将在 Capability 与 subModules 注入后才会执行
 *     </li>
 *     <li>
 *         <p>{@link ModuleCheckFactory#activateModelImplCheck()}</p>
 *         检查实现了 {@link ActivateModel} 的模块数量、名称与prompt是否一致
 *     </li>
 * </ol>
 *
 * <p>下一步流程请参阅{@link ModuleRegisterFactory}</p>
 */
public class ModuleCheckFactory extends AgentBaseFactory {

    private Reflections reflections;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        reflections = context.getReflections();
    }

    @Override
    protected void run() {
        Set<Class<?>> moduleTypes = reflections.getTypesAnnotatedWith(AgentModule.class);
        Set<Class<?>> subModuleTypes = reflections.getTypesAnnotatedWith(AgentSubModule.class);
        //检查注解AgentModule或AgentSubModule所在类是否继承了对应的抽象类
        annotationAbstractCheck(moduleTypes, AgentRunningModule.class);
        annotationAbstractCheck(subModuleTypes, AgentRunningSubModule.class);
        //检查AgentModule是否具备无参构造方法
        moduleConstructorsCheck(moduleTypes);
        moduleConstructorsCheck(subModuleTypes);
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
        Set<Class<?>> types = getMethodAnnotationTypeSet(AgentModule.class, reflections);
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

    private void annotationAbstractCheck(Set<Class<?>> types, Class<?> clazz) {
        for (Class<?> type : types) {
            if (type.isAnnotation()) {
                continue;
            }
            if (clazz.isAssignableFrom(type) && ClassUtil.isNormalClass(type)) {
                continue;
            }
            throw new ModuleCheckException("存在未继承AgentInteractionModule.class的AgentModule实现: " + type.getSimpleName());
        }
    }
}
