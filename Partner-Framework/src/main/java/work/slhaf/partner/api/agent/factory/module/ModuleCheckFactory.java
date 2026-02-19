package work.slhaf.partner.api.agent.factory.module;

import cn.hutool.core.util.ClassUtil;
import org.reflections.Reflections;
import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.module.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.factory.module.abstracts.AgentRunningModule;
import work.slhaf.partner.api.agent.factory.module.abstracts.AgentRunningSubModule;
import work.slhaf.partner.api.agent.factory.module.annotation.AfterExecute;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.factory.module.annotation.BeforeExecute;
import work.slhaf.partner.api.agent.factory.module.exception.ModuleCheckException;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigManager;

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
        AnnotatedModules annotatedModules = getAnnotatedModules();
        ExtendedModules extendedModules = getExtendedModules();
        checkIfClassCorresponds(annotatedModules, extendedModules);
        //检查注解AgentModule或AgentSubModule所在类是否继承了对应的抽象类
        annotationAbstractCheck(annotatedModules.moduleTypes(), AgentRunningModule.class);
        annotationAbstractCheck(annotatedModules.subModuleTypes(), AgentRunningSubModule.class);
        //检查AgentModule是否具备无参构造方法
        moduleConstructorsCheck(annotatedModules.moduleTypes());
        moduleConstructorsCheck(annotatedModules.subModuleTypes());
        //检查实现了ActivateModel的模块数量、名称与prompt是否一致
        activateModelImplCheck();
        //检查hook注解所在位置是否正确
        hookLocationCheck();
    }

    private ExtendedModules getExtendedModules() {
        Set<Class<?>> moduleTypes = reflections.getSubTypesOf(AgentRunningModule.class)
                .stream()
                .filter(ClassUtil::isNormalClass)
                .collect(Collectors.toSet());
        Set<Class<?>> subModuleTypes = reflections.getSubTypesOf(AgentRunningSubModule.class)
                .stream()
                .filter(ClassUtil::isNormalClass)
                .collect(Collectors.toSet());
        return new ExtendedModules(moduleTypes, subModuleTypes);
    }

    private AnnotatedModules getAnnotatedModules() {
        Set<Class<?>> moduleTypes = reflections.getTypesAnnotatedWith(AgentModule.class)
                .stream()
                .filter(ClassUtil::isNormalClass)
                .collect(Collectors.toSet());
        Set<Class<?>> subModuleTypes = reflections.getTypesAnnotatedWith(AgentSubModule.class)
                .stream()
                .filter(ClassUtil::isNormalClass)
                .collect(Collectors.toSet());
        return new AnnotatedModules(moduleTypes, subModuleTypes);
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

    private void checkIfClassCorresponds(AnnotatedModules annotatedModules, ExtendedModules extendedModules) {
        // 检查是否有被@AgentModule注解但没有继承AgentRunningModule的类
        checkSets(annotatedModules.moduleTypes(), extendedModules.moduleTypes(), 
                 "存在被@AgentModule注解但未继承AgentRunningModule的类");
        
        // 检查是否有继承AgentRunningModule但没有被@AgentModule注解的类
        checkSets(extendedModules.moduleTypes(), annotatedModules.moduleTypes(),
                 "存在继承AgentRunningModule但未被@AgentModule注解的类");
        
        // 检查是否有被@AgentSubModule注解但没有继承AgentRunningSubModule的类
        checkSets(annotatedModules.subModuleTypes(), extendedModules.subModuleTypes(),
                 "存在被@AgentSubModule注解但未继承AgentRunningSubModule的类");
        
        // 检查是否有继承AgentRunningSubModule但没有被@AgentSubModule注解的类
        checkSets(extendedModules.subModuleTypes(), annotatedModules.subModuleTypes(),
                 "存在继承AgentRunningSubModule但未被@AgentSubModule注解的类");
    }
    
    /**
     * 检查源集合中是否有不在目标集合中的元素
     * @param source 源集合
     * @param target 目标集合
     * @param errorMessage 错误信息前缀
     */
    private void checkSets(Set<Class<?>> source, Set<Class<?>> target, String errorMessage) {
        // 只有在需要时才创建HashSet以节省内存
        if (!target.containsAll(source)) {
            // 使用流式处理找出差异部分，避免创建完整的中间集合
            String classNames = source.stream()
                    .filter(clazz -> !target.contains(clazz))
                    .map(Class::getSimpleName)
                    .limit(10) // 限制显示数量，避免信息泄露
                    .collect(Collectors.joining(", ", "[", "]"));
            
            throw new ModuleCheckException(errorMessage + ": " + classNames);
        }
    }

    private record AnnotatedModules(Set<Class<?>> moduleTypes, Set<Class<?>> subModuleTypes) {
    }

    private record ExtendedModules(Set<Class<?>> moduleTypes, Set<Class<?>> subModuleTypes) {
    }
}
