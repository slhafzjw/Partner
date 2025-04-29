package work.slhaf.agent.core.interaction;

import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.config.ModuleConfig;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class InteractionModulesLoader {

    private static InteractionModulesLoader interactionModulesLoader;

    public static InteractionModulesLoader getInstance(){
        if (interactionModulesLoader == null) {
            interactionModulesLoader = new InteractionModulesLoader();
        }
        return interactionModulesLoader;
    }

    public List<InteractionModule> registerInteractionModules() throws IOException {
        List<InteractionModule> moduleList = new ArrayList<>();
        List<ModuleConfig> moduleConfigList = Config.getConfig().getModuleConfigList();
        for (ModuleConfig moduleConfig : moduleConfigList) {
            if (ModuleConfig.Constant.INTERNAL.equals(moduleConfig.getType())) {
                moduleList.add(loadInternalModule(moduleConfig.getClassName()));
            } else if (ModuleConfig.Constant.EXTERNAL.equals(moduleConfig.getType())) {
                moduleList.add(loadExternalModule(moduleConfig.getClassName(),moduleConfig.getPath()));
            }
        }
        return moduleList;
    }

    private InteractionModule loadExternalModule(String className, String path) {
        try {
            URL jarUrl = new File(path).toURI().toURL();
            URLClassLoader loader = new URLClassLoader(new URL[]{jarUrl}, this.getClass().getClassLoader());

            Class<?> clazz = loader.loadClass(className);
            loader.close();
            return (InteractionModule) clazz.getMethod("getInstance").invoke(null);
        } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException |
                 NoSuchMethodException | IOException e) {
            throw new RuntimeException("Fail to load internal module: " + className ,e);
        }
    }

    private static InteractionModule loadInternalModule(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return (InteractionModule) clazz.getMethod("getInstance").invoke(null);
        } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException("Fail to load internal module: " + className,e);
        }
    }
}
