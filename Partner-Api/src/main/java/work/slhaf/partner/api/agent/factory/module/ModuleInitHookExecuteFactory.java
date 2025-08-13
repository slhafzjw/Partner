package work.slhaf.partner.api.agent.factory.module;

import work.slhaf.partner.api.agent.factory.AgentBaseFactory;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.context.ModuleFactoryContext;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.factory.module.exception.ModuleInitHookExecuteFailedException;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaMethod;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static work.slhaf.partner.api.agent.util.AgentUtil.collectExtendedClasses;
import static work.slhaf.partner.api.agent.util.AgentUtil.methodSignature;

/**
 * 负责执行前hook逻辑
 */
public class ModuleInitHookExecuteFactory extends AgentBaseFactory {

    private List<MetaModule> moduleList;

    @Override
    protected void setVariables(AgentRegisterContext context) {
        ModuleFactoryContext factoryContext = context.getModuleFactoryContext();
        moduleList = factoryContext.getModuleList();
    }

    @Override
    protected void run() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        //遍历模块列表，并向上查找@Init注解
        for (MetaModule metaModule : moduleList) {
            List<MetaMethod> initHookMethods = collectInitHookMethods(metaModule.getClazz());
            proceedInitMethods(metaModule, initHookMethods);
        }
    }

    private void proceedInitMethods(MetaModule metaModule, List<MetaMethod> initHookMethods) {
        for (MetaMethod metaMethod : initHookMethods) {
            try {
                metaMethod.getMethod().invoke(metaModule.getInstance());
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new ModuleInitHookExecuteFailedException("模块的init hook方法执行失败! 模块: " + metaModule.getName() + " 方法签名: " + methodSignature(metaMethod.getMethod()), e);
            }
        }
    }

    private List<MetaMethod> collectInitHookMethods(Class<?> clazz) {
        Set<Class<?>> classes = collectExtendedClasses(clazz, AgentRunningModule.class);
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
