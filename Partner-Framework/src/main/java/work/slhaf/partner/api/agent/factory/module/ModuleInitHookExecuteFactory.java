package work.slhaf.partner.api.agent.factory.module;

import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.AgentRegisterFactory;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.context.ModuleFactoryContext;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.factory.module.exception.ModuleInitHookExecuteFailedException;
import work.slhaf.partner.api.agent.factory.module.pojo.BaseMetaModule;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaMethod;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.Module;
import work.slhaf.partner.api.agent.util.AgentUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static work.slhaf.partner.api.agent.util.AgentUtil.collectExtendedClasses;
import static work.slhaf.partner.api.agent.util.AgentUtil.methodSignature;

/**
 * <h2>Agent启动流程 7</h2>
 *
 * <p>负责执行初始化hook逻辑，即 {@link Init} 注解所在方法</p>
 *
 * <ol>
 *     <li>
 *         <p>{@link ModuleInitHookExecuteFactory#collectInitHookMethods(Class, Class)}</p>
 *         分别遍历前置模块拿到的模块列表({@link ModuleInitHookExecuteFactory#moduleList}, {@link ModuleInitHookExecuteFactory#subModuleList})，通过 {@link AgentUtil#collectExtendedClasses(Class, Class)} 收集到当前模块类的继承链上的所有类后，收集其所有带有 {@link Init} 注解的方法
 *     </li>
 *     <li>
 *         <p>{@link ModuleInitHookExecuteFactory#proceedInitMethods(BaseMetaModule, List)}</p>
 *         收集好初始化方法后，将通过反射执行该方法，所用实例即为前置模块中收集到的执行模块与子模块的 {@link MetaModule} 与 {@link MetaSubModule} 内容
 *     </li>
 * </ol>
 *
 * <p>Agent启动流程到此进行完毕。整个工厂执行链中均为针对 {@link AgentRegisterContext} 进行的操作，在 {@link AgentRegisterFactory} 中，将进行最终处理以及将必要内容进行传递。</p>
 */
public class ModuleInitHookExecuteFactory extends AgentBaseFactory {

    private List<MetaModule> moduleList;
    private List<MetaSubModule> subModuleList;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        ModuleFactoryContext factoryContext = context.getModuleFactoryContext();
        moduleList = factoryContext.getAgentModuleList();
        subModuleList = factoryContext.getAgentSubModuleList();
    }

    @Override
    protected void run() {
        //遍历模块列表，并向上查找@Init注解
        for (MetaSubModule metaSubModule : subModuleList) {
            List<MetaMethod> initHookMethods = collectInitHookMethods(metaSubModule.getClazz(),AgentRunningModule.class);
            proceedInitMethods(metaSubModule, initHookMethods);
        }

        for (MetaModule metaModule : moduleList) {
            List<MetaMethod> initHookMethods = collectInitHookMethods(metaModule.getClazz(), AgentRunningSubModule.class);
            proceedInitMethods(metaModule, initHookMethods);
        }
    }

    private void proceedInitMethods(BaseMetaModule metaModule, List<MetaMethod> initHookMethods) {
        for (MetaMethod metaMethod : initHookMethods) {
            try {
                metaMethod.getMethod().invoke(metaModule.getInstance());
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new ModuleInitHookExecuteFailedException("模块的init hook方法执行失败! 模块: " + metaModule.getClazz().getSimpleName() + " 方法签名: " + methodSignature(metaMethod.getMethod()), e);
            }
        }
    }

    private List<MetaMethod> collectInitHookMethods(Class<?> clazz, Class<? extends Module> target) {
        Set<Class<?>> classes = collectExtendedClasses(clazz, target);
        return classes.stream()
                .map(Class::getDeclaredMethods)
                .flatMap(Arrays::stream)
                .filter(method -> method.isAnnotationPresent(Init.class))
                .map(method -> {
                    MetaMethod metaMethod = new MetaMethod();
                    metaMethod.setMethod(method);
                    metaMethod.setOrder(method.getAnnotation(Init.class).order());
                    return metaMethod;
                })
                .sorted(Comparator.comparing(MetaMethod::getOrder))
                .collect(Collectors.toList());
    }
}
