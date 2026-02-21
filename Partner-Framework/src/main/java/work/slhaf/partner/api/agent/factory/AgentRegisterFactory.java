package work.slhaf.partner.api.agent.factory;

import org.reflections.util.ClasspathHelper;
import work.slhaf.partner.api.agent.factory.capability.CapabilityCheckFactory;
import work.slhaf.partner.api.agent.factory.capability.CapabilityInjectFactory;
import work.slhaf.partner.api.agent.factory.capability.CapabilityRegisterFactory;
import work.slhaf.partner.api.agent.factory.component.ComponentAnnotationValidatorFactory;
import work.slhaf.partner.api.agent.factory.component.ComponentRegisterFactory;
import work.slhaf.partner.api.agent.factory.component.ModuleInitHookExecuteFactory;
import work.slhaf.partner.api.agent.factory.config.ConfigLoaderFactory;
import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.agent.factory.exception.ExternalModuleLoadFailedException;
import work.slhaf.partner.api.agent.factory.exception.ExternalModulePathNotExistException;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigLoader;
import work.slhaf.partner.api.agent.runtime.interaction.flow.AgentRunningFlow;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * <h2>Agent 注册工厂</h2>
 *
 * <p>
 * 具体流程依次按照 {@link AgentRegisterFactory#launch(String)} 方法顺序执行，最终将执行模块列表对应实例交给 {@link AgentConfigLoader} ，传递给 {@link AgentRunningFlow} 针对交互做出调用
 * <p/>
 */
public class AgentRegisterFactory {

    private static final List<URL> urls = new ArrayList<>();

    private AgentRegisterFactory() {
    }

    public static void launch(String packageName) {
        urls.addAll(packageNameToURL(packageName));
        AgentRegisterContext registerContext = new AgentRegisterContext(urls);
        //流程
        //0. 加载配置
        new ConfigLoaderFactory().execute(registerContext);
        //1. 校验 Component 级别注解是否合规，避免注入到异常位置
        new ComponentAnnotationValidatorFactory().execute(registerContext);
        //2. 收集所有的 AgentComponent 实例
        new ComponentRegisterFactory().execute(registerContext);
        //3. 加载检查Capability层内容后进行能力层的内容注册
        new CapabilityCheckFactory().execute(registerContext);
        new CapabilityRegisterFactory().execute(registerContext);
        //. 先一步注入Capability,避免因前hook逻辑存在针对能力的引用而报错
        new CapabilityInjectFactory().execute(registerContext);
        //. 执行模块PreHook逻辑
        new ModuleInitHookExecuteFactory().execute(registerContext);

    }


    /**
     * 添加可扫描包
     *
     * @param packageName 指定的包名
     */
    public static void addScanPackage(String packageName) {
        urls.addAll(packageNameToURL(packageName));
    }

    /**
     * 添加外部模块目录
     *
     * @param externalPackagePath 指定的外部模块目录路径
     */
    public static void addScanDir(String externalPackagePath) {
        File file = new File(externalPackagePath);
        if (!file.exists() || !file.isDirectory()) {
            throw new ExternalModulePathNotExistException("不存在的外部模块目录: " + externalPackagePath);
        }
        try {
            File[] files = file.listFiles();
            if (files == null || files.length == 0) {
                throw new ExternalModulePathNotExistException("外部模块目录为空: " + externalPackagePath);
            }
            for (File f : files) {
                if (f.getName().endsWith(".jar")) {
                    urls.add(f.toURI().toURL());
                }
            }
        } catch (Exception e) {
            throw new ExternalModuleLoadFailedException("外部模块URL获取失败: " + externalPackagePath, e);
        }
    }

    private static List<URL> packageNameToURL(String packageName) {
        return ClasspathHelper.forPackage(packageName).stream().toList();
    }

}
