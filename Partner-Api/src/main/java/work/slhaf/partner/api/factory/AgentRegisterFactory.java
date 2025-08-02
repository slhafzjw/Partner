package work.slhaf.partner.api.factory;

import cn.hutool.core.bean.BeanUtil;
import work.slhaf.partner.api.entity.AgentContext;
import work.slhaf.partner.api.factory.capability.CapabilityCheckFactory;
import work.slhaf.partner.api.factory.capability.CapabilityRegisterFactory;
import work.slhaf.partner.api.factory.config.ConfigLoaderFactory;
import work.slhaf.partner.api.factory.entity.AgentRegisterContext;
import work.slhaf.partner.api.factory.module.ModuleCheckFactory;
import work.slhaf.partner.api.factory.module.ModuleRegisterFactory;

public class AgentRegisterFactory {

    private AgentRegisterFactory() {
    }

    public static AgentContext launch(String path) {
        AgentRegisterContext registerContext = new AgentRegisterContext(path);
        //流程
        //0. 加载配置
        new ConfigLoaderFactory().execute(registerContext);
        //1. 执行register和check逻辑
        new CapabilityRegisterFactory().execute(registerContext);
        new CapabilityCheckFactory().execute(registerContext);
        new ModuleRegisterFactory().execute(registerContext);
        new ModuleCheckFactory().execute(registerContext);

        //2. 为module通过动态代理添加后hook逻辑并进行实例化

        //3. 先一步注入Capability,避免因前hook逻辑存在针对能力的引用而报错

        //4. 执行前hook逻辑


        AgentContext agentContext = new AgentContext();
        BeanUtil.copyProperties(registerContext,agentContext);
        return agentContext;
    }

}
