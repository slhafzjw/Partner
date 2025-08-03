package work.slhaf.partner.api.factory.module;

import work.slhaf.partner.api.factory.entity.AgentBaseFactory;
import work.slhaf.partner.api.factory.entity.AgentRegisterContext;

import java.lang.reflect.InvocationTargetException;

/**
 * 通过扫描注解<code>@Before</code>，获取到各个模块的后hook逻辑并通过动态代理添加到执行逻辑之后
 */
public class ModuleProxyFactory extends AgentBaseFactory {
    @Override
    protected void setVariables(AgentRegisterContext context) {

    }

    @Override
    protected void run() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        //TODO 通过动态代理生成实例、添加PostHook逻辑
    }
}
