package work.slhaf.partner.core.interaction.module;

import work.slhaf.partner.common.config.Config;
import work.slhaf.partner.common.config.ModuleConfig;

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

    public List<InteractionFlow> registerInteractionModules() throws IOException {
        List<InteractionFlow> moduleList = new ArrayList<>();
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

    private InteractionFlow loadExternalModule(String className, String path) {
        try {
            URL jarUrl = new File(path).toURI().toURL();
            URLClassLoader loader = new URLClassLoader(new URL[]{jarUrl}, this.getClass().getClassLoader());

            Class<?> clazz = loader.loadClass(className);
            loader.close();
            return (InteractionFlow) clazz.getMethod("getInstance").invoke(null);
        } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException |
                 NoSuchMethodException | IOException e) {
            throw new RuntimeException("Fail to load internal module: " + className ,e);
        }
    }

    private static InteractionFlow loadInternalModule(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return (InteractionFlow) clazz.getMethod("getInstance").invoke(null);
        } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException("Fail to load internal module: " + className,e);
        }
    }
}
