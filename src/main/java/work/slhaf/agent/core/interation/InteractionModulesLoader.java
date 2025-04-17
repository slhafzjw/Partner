package work.slhaf.agent.core.interation;

import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.config.ModuleConfig;
import work.slhaf.module.InteractionModule;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public class InteractionModulesLoader {
    public static List<InteractionModule> registerInteractionModules() throws IOException {
        List<InteractionModule> moduleList = new ArrayList<>();
        List<ModuleConfig> moduleConfigList = Config.getConfig().getModuleConfigList();
        for (ModuleConfig moduleConfig : moduleConfigList) {
            if (ModuleConfig.Constant.INTERNAL.equals(moduleConfig.getType())) {
                moduleList.add(loadInternalModule(moduleConfig.getClassName()));
            }
        }
        return moduleList;
    }

    private static InteractionModule loadInternalModule(String moduleName) {
        try {
            Class<?> clazz = Class.forName(moduleName);

            //TODO 后续需要规范`getInstance`方法的实现
            return (InteractionModule) clazz.getMethod("getInstance").invoke(null);
        } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException("Fail to load internal module: " + moduleName,e);
        }
    }
}
