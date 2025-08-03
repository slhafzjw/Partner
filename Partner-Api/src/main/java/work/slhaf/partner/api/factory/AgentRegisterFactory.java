package work.slhaf.partner.api.factory;

import cn.hutool.core.bean.BeanUtil;
import work.slhaf.partner.api.entity.AgentContext;
import work.slhaf.partner.api.factory.capability.CapabilityCheckFactory;
import work.slhaf.partner.api.factory.capability.CapabilityInjectFactory;
import work.slhaf.partner.api.factory.capability.CapabilityRegisterFactory;
import work.slhaf.partner.api.factory.config.ConfigLoaderFactory;
import work.slhaf.partner.api.factory.entity.AgentRegisterContext;
import work.slhaf.partner.api.factory.exception.ExternalModulePathNotExistException;
import work.slhaf.partner.api.factory.module.ModuleCheckFactory;
import work.slhaf.partner.api.factory.module.ModuleProxyFactory;
import work.slhaf.partner.api.factory.module.ModulePreHookExecuteFactory;
import work.slhaf.partner.api.factory.module.ModuleRegisterFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AgentRegisterFactory {

    private static List<String> paths = new ArrayList<>();

    private AgentRegisterFactory() {
    }

    public static AgentContext launch(String path) {
        paths.add(path);
        AgentRegisterContext registerContext = new AgentRegisterContext(paths);
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
        BeanUtil.copyProperties(registerContext,agentContext);
        return agentContext;
    }

    //TODO 也需要可指定路径，当前只是新增了可扫描包
    public static void addScanPath(String path) {
        File file = new File(path);
        if (!file.exists() || !file.isDirectory()) {
            throw new ExternalModulePathNotExistException("不存在的外部模块目录: "+path);
        }
        paths.add(path);
    }

}
