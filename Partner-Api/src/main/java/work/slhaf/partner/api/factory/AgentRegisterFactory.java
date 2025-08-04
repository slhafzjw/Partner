package work.slhaf.partner.api.factory;

import cn.hutool.core.bean.BeanUtil;
import org.reflections.util.ClasspathHelper;
import work.slhaf.partner.api.entity.AgentContext;
import work.slhaf.partner.api.factory.capability.CapabilityCheckFactory;
import work.slhaf.partner.api.factory.capability.CapabilityInjectFactory;
import work.slhaf.partner.api.factory.capability.CapabilityRegisterFactory;
import work.slhaf.partner.api.factory.config.ConfigLoaderFactory;
import work.slhaf.partner.api.factory.context.AgentRegisterContext;
import work.slhaf.partner.api.factory.exception.ExternalModuleLoadFailedException;
import work.slhaf.partner.api.factory.exception.ExternalModulePathNotExistException;
import work.slhaf.partner.api.factory.module.ModuleCheckFactory;
import work.slhaf.partner.api.factory.module.ModulePreHookExecuteFactory;
import work.slhaf.partner.api.factory.module.ModuleProxyFactory;
import work.slhaf.partner.api.factory.module.ModuleRegisterFactory;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class AgentRegisterFactory {

    private static final List<URL> urls = new ArrayList<>();

    private AgentRegisterFactory() {
    }

    public static AgentContext launch(String packageName) {
        urls.addAll(packageNameToURL(packageName));
        AgentRegisterContext registerContext = new AgentRegisterContext(urls);
        //流程
        //0. 加载配置
        new ConfigLoaderFactory().execute(registerContext);
        //1. 注册并检查Capability
        new CapabilityRegisterFactory().execute(registerContext);
        new CapabilityCheckFactory().execute(registerContext);
        //2. 注册并检查Module
        new ModuleCheckFactory().execute(registerContext);
        new ModuleRegisterFactory().execute(registerContext);
        //3. 为module通过动态代理添加PostHook逻辑并进行实例化
        new ModuleProxyFactory().execute(registerContext);
        //. 先一步注入Capability,避免因前hook逻辑存在针对能力的引用而报错
        new CapabilityInjectFactory().execute(registerContext);
        //. 执行模块PreHook逻辑
        new ModulePreHookExecuteFactory().execute(registerContext);

        AgentContext agentContext = new AgentContext();
        BeanUtil.copyProperties(registerContext, agentContext);
        return agentContext;
    }

    /**
     * 添加可扫描包
     * @param packageName 指定的包名
     */
    public static void addScanPackage(String packageName) {
        urls.addAll(packageNameToURL(packageName));
    }

    /**
     * 添加外部模块目录
     * @param externalPackagePath 指定的外部模块目录路径
     */
    public static void addScanDir(String externalPackagePath) {
        File file = new File(externalPackagePath);
        if (!file.exists() || !file.isDirectory()) {
            throw new ExternalModulePathNotExistException("不存在的外部模块目录: " + externalPackagePath);
        }
        try {
            for (File f : file.listFiles()) {
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
